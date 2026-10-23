package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.*;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /** Result of one Dijkstra run: the hops from this router to the destination and each hop's cost. */
  private static class PathResult {
    final LinkedList<String> nodes = new LinkedList<>();
    final LinkedList<Integer> hopCosts = new LinkedList<>();
  }

  /**
   * Runs Dijkstra over the LSAs currently in the database, using link weights (not hop count).
   * Each LSA contributes edges from its originating router to every router in its link list.
   * The self-link (weight 0) that every router starts with is skipped.
   *
   * @return the path from this router to destinationIP, or null if it is unreachable
   */
  private synchronized PathResult computeShortestPath(String destinationIP) {
    String source = rd.simulatedIPAddress;
    Map<String, Integer> dist = new HashMap<>();   // best known cost to each router
    Map<String, String> prev = new HashMap<>();    // previous hop on the best known path
    Set<String> visited = new HashSet<>();
    dist.put(source, 0);

    while (true) {
      // pick the unvisited router with the smallest tentative distance
      String u = null;
      for (Map.Entry<String, Integer> e : dist.entrySet()) {
        if (!visited.contains(e.getKey()) && (u == null || e.getValue() < dist.get(u))) {
          u = e.getKey();
        }
      }
      if (u == null) break;          // nothing left to explore
      visited.add(u);
      if (u.equals(destinationIP)) break;

      LSA lsa = _store.get(u);
      if (lsa == null) continue;     // we know u exists, but not its links (yet)

      // relax every edge advertised by u
      for (LinkDescription ld : lsa.links) {
        if (ld.linkID.equals(u)) continue;   // skip self-link
        int alt = dist.get(u) + ld.weight;
        Integer current = dist.get(ld.linkID);
        if (current == null || alt < current) {
          dist.put(ld.linkID, alt);
          prev.put(ld.linkID, u);
        }
      }
    }

    if (!visited.contains(destinationIP)) {
      return null;                   // never reached: no path
    }

    // walk back from the destination to the source to rebuild the path
    PathResult result = new PathResult();
    String node = destinationIP;
    result.nodes.addFirst(node);
    while (prev.containsKey(node)) {
      String p = prev.get(node);
      result.hopCosts.addFirst(dist.get(node) - dist.get(p));
      result.nodes.addFirst(p);
      node = p;
    }
    return result;
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   * <p/>
   * This method should use Dijkstra's algorithm with link weights (not hop count) to find the shortest path.
   * The weights are stored in the weight field of LinkDescription objects in the LSA entries.
   * <p/>
   * format: source ip address -> ip address -> ... -> destination ip
   *
   * @param destinationIP the simulated IP address of the destination router
   * @return the shortest path as a string, or null if no path exists
   */
  synchronized String getShortestPath(String destinationIP) {
    PathResult r = computeShortestPath(destinationIP);
    if (r == null) return null;
    StringBuilder sb = new StringBuilder(r.nodes.get(0));
    for (int i = 1; i < r.nodes.size(); i++) {
      sb.append(" -> (").append(r.hopCosts.get(i - 1)).append(") ").append(r.nodes.get(i));
    }
    return sb.toString();
  }

  /**
   * @return the simulated IP of the first hop on the shortest path to destinationIP,
   *         or null if there is no path (or the destination is this router)
   */
  synchronized String getNextHop(String destinationIP) {
    PathResult r = computeShortestPath(destinationIP);
    if (r == null || r.nodes.size() < 2) return null;
    return r.nodes.get(1);
  }

  // Update LSA if newer than existing entry; ignore echoes of our own LSA
  public synchronized boolean updateLSA(LSA incomingLSA) {
    if (incomingLSA == null || incomingLSA.linkStateID == null) {
      return false;
    }
    if (incomingLSA.linkStateID.equals(rd.simulatedIPAddress)) {
      return false;
    }
    LSA current = _store.get(incomingLSA.linkStateID);
    if (current == null || incomingLSA.lsaSeqNumber > current.lsaSeqNumber) {
      _store.put(incomingLSA.linkStateID, incomingLSA);
      return true;
    }
    return false;
  }

  public synchronized void putLSA(LSA lsa) {
    if (lsa != null && lsa.linkStateID != null) {
      _store.put(lsa.linkStateID, lsa);
    }
  }

  public synchronized LSA getLSA(String routerIP) {
    return _store.get(routerIP);
  }

  public synchronized java.util.Vector<LSA> getAllLSAs() {
    return new java.util.Vector<LSA>(_store.values());
  }

  public synchronized HashMap<String, LSA> getStore() {
    return _store;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    ld.weight = 0; //self-link has weight 0
    lsa.links.add(ld);
    return lsa;
  }


  public synchronized String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.weight).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
