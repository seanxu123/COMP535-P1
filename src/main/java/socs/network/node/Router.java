package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  // assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  private ServerSocket serverSocket;
  private final BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

  // used for attach request Y/N prompting between threads
  private boolean hasPendingAttachRequest = false;
  private Boolean attachRequestAnswer = null;
  private final Object attachLock = new Object();

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    lsd = new LinkStateDatabase(rd);

    // set up server socket on any available port
    try {
      serverSocket = new ServerSocket(0);
      rd.processIPAddress = InetAddress.getLocalHost().getHostAddress();
      rd.processPortNumber = serverSocket.getLocalPort();
    } catch (Exception e) {
      System.err.println("Failed to create server socket: " + e.getMessage());
      System.exit(1);
    }

    // print router info so user can run attach from other terminals
    System.out.println("========================================");
    System.out.println("Process IP  : " + rd.processIPAddress);
    System.out.println("Process Port: " + rd.processPortNumber);
    System.out.println("Simulated IP: " + rd.simulatedIPAddress);
    System.out.println("========================================");

    // start listening for incoming connections in background
    Thread listenerThread = new Thread(() -> requestHandler());
    listenerThread.setDaemon(true);
    listenerThread.start();
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, int processPort,
                             String simulatedIP, short weight) {
    // Check for duplicate attachment and find a free port atomically
    int freePort = -1;
    synchronized (ports) {
      for (Link link : ports) {
        if (link != null && link.router2.simulatedIPAddress.equals(simulatedIP)) {
          System.out.println("Already attached to " + simulatedIP);
          return;
        }
      }
      for (int i = 0; i < ports.length; i++) {
        if (ports[i] == null) {
          freePort = i;
          break;
        }
      }
    }
    if (freePort == -1) {
      System.out.println("All ports are occupied, cannot attach.");
      return;
    }

    try {
      // Open TCP connection to the remote router's server socket
      Socket socket = new Socket(processIP, processPort);
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

      // Build and send HELLO packet to request attachment
      SOSPFPacket hello = makeHelloPacket(simulatedIP, weight);
      out.writeObject(hello);
      out.flush();

      // Wait for accept/reject response
      SOSPFPacket response = (SOSPFPacket) in.readObject();

      if (response.sospfType == 0) {
        // Accepted — create the link and store it
        RouterDescription remoteRd = new RouterDescription();
        remoteRd.processIPAddress = processIP;
        remoteRd.processPortNumber = processPort;
        remoteRd.simulatedIPAddress = simulatedIP;

        final Link link = new Link(rd, remoteRd, weight, socket, out, in);
        synchronized (ports) {
          ports[freePort] = link;
        }
        System.out.println("Your attach request has been accepted;");

        Thread listener = new Thread(() -> listenOnLink(link));
        listener.setDaemon(true);
        listener.start();
      } else {
        // Rejected
        System.out.println("Your attach request has been rejected;");
        socket.close();
      }
    } catch (Exception e) {
      System.err.println("Failed to attach to " + simulatedIP + ": " + e.getMessage());
    }
  }

  private SOSPFPacket makeHelloPacket(String dstIP, int weight) {
    SOSPFPacket pkt = new SOSPFPacket();
    pkt.srcProcessIP = rd.processIPAddress;
    pkt.srcProcessPort = rd.processPortNumber;
    pkt.srcIP = rd.simulatedIPAddress;
    pkt.dstIP = dstIP;
    pkt.sospfType = 0;
    pkt.routerID = rd.simulatedIPAddress;
    pkt.neighborID = rd.simulatedIPAddress;
    pkt.weight = weight;
    return pkt;
  }

  private SOSPFPacket makeRejectPacket() {
    SOSPFPacket pkt = new SOSPFPacket();
    pkt.sospfType = -1;
    return pkt;
  }

  /**
   * process request from the remote router.
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request.
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   *
   * Runs in a background daemon thread. Loops forever accepting incoming TCP connections
   * and spawns a new thread to handle each one.
   */
  private void requestHandler() {
    while (true) {
      try {
        Socket clientSocket = serverSocket.accept();
        Thread handler = new Thread(() -> handleIncomingConnection(clientSocket));
        handler.setDaemon(true);
        handler.start();
      } catch (Exception e) {
        System.err.println("Error accepting connection: " + e.getMessage());
      }
    }
  }

  private void handleIncomingConnection(Socket clientSocket) {
    try {
      ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
      ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

      SOSPFPacket packet = (SOSPFPacket) in.readObject();
      if (packet.sospfType == 0) {
        handleHello(packet, clientSocket, in, out);
      }
    } catch (Exception e) {
      System.err.println("Error handling incoming connection: " + e.getMessage());
      try { clientSocket.close(); } catch (Exception ignored) {}
    }
  }

  private void handleHello(SOSPFPacket packet, Socket clientSocket, ObjectInputStream in, ObjectOutputStream out) throws Exception {
    String senderSimIP = packet.neighborID;

    // Check if link already exists
    boolean alreadyAttached;
    synchronized (ports) {
      alreadyAttached = false;
      for (Link link : ports) {
        if (link != null && link.router2.simulatedIPAddress.equals(senderSimIP)) {
          alreadyAttached = true;
          break;
        }
      }
    }

    if (alreadyAttached) {
      // Already attached: HELLO belongs to start handshake
      return;
    }

    // New attach request — prompt user for Y/N
    System.out.println("received HELLO from " + senderSimIP + ";");
    System.out.println("Do you accept this request? (Y/N)");

    // Set the pending flag and wait for the terminal thread to read the answer
    synchronized (attachLock) {
      hasPendingAttachRequest = true;
      attachRequestAnswer = null;
      while (attachRequestAnswer == null) {
        attachLock.wait();
      }
      boolean accepted = attachRequestAnswer;
      hasPendingAttachRequest = false;

      if (accepted) {
        // Find a free port and store the link atomically
        int freePort = -1;
        Link newLink = null;
        synchronized (ports) {
          for (int i = 0; i < ports.length; i++) {
            if (ports[i] == null) {
              freePort = i;
              break;
            }
          }
          if (freePort != -1) {
            RouterDescription remoteRd = new RouterDescription();
            remoteRd.processIPAddress = packet.srcProcessIP;
            remoteRd.processPortNumber = packet.srcProcessPort;
            remoteRd.simulatedIPAddress = senderSimIP;
            newLink = new Link(rd, remoteRd, packet.weight, clientSocket, out, in);
            ports[freePort] = newLink;
          }
        }
        if (freePort == -1) {
          // All ports occupied even though user said Y
          System.out.println("All ports are occupied, rejecting.");
          out.writeObject(makeRejectPacket());
          out.flush();
          clientSocket.close();
        } else {
          // Send HELLO back to confirm acceptance
          out.writeObject(makeHelloPacket(senderSimIP, packet.weight));
          out.flush();
          final Link link = newLink;
          Thread listener = new Thread(() -> listenOnLink(link));
          listener.setDaemon(true);
          listener.start();
        }
      } else {
        System.out.println("You rejected the attach request;");
        out.writeObject(makeRejectPacket());
        out.flush();
        clientSocket.close();
      }
    }
  }

  private void listenOnLink(Link link){
      try{
        while (true) {
          SOSPFPacket packet = (SOSPFPacket) link.ois.readObject();
          if (packet.sospfType == 0){
            handleHelloOnLink(link, packet);
          }
        }
      } catch (Exception e){
        System.out.println("Exception occurred when trying to listen on link: " + e);
      }
  }

  private synchronized void handleHelloOnLink(Link link, SOSPFPacket packet){
    RouterDescription neighbor = link.router2;
    System.out.println("received HELLO from " + packet.srcIP + ";");

    if (neighbor.status == null){
      if (link.startedByUs){
        neighbor.status = RouterStatus.TWO_WAY;
        System.out.println("set " + packet.srcIP + " STATE to TWO_WAY;");
        sendHello(link);
      } else {
        neighbor.status = RouterStatus.INIT;
        System.out.println("set " + packet.srcIP + " STATE to INIT;");
        sendHello(link);
      }
    } else if (neighbor.status == RouterStatus.INIT) {
      neighbor.status = RouterStatus.TWO_WAY;
      System.out.println("set " + packet.srcIP + " STATE to TWO_WAY;");
    }
  }

  private void sendHello(Link link){
    try {
      synchronized (link) {
        link.oos.writeObject(makeHelloPacket(link.router2.simulatedIPAddress, link.weight));
        link.oos.flush();
      }
    } catch (Exception e){
      System.err.println("Failed to send HELLO to " + link.router2.simulatedIPAddress);
    }
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    synchronized (ports) {
      for (Link link : ports) {
        if (link != null){
          link.startedByUs = true;
          sendHello(link);
        }
      }
    }
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, int processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    synchronized (ports) {
      for (Link link : ports) {
        if (link != null && link.router2.status == RouterStatus.TWO_WAY) {
          System.out.println(link.router2.simulatedIPAddress);
        }
      }
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, int processPort,
                             String simulatedIP, short weight){

  }

  /**
   * update the weight of a specific port.
   * This change should trigger synchronization of the Link State Database by sending
   * a Link State Advertisement (LSA) update to all neighboring routers in the topology.
   *
   * @param portNumber the port number (0-3) to update
   * @param newWeight the new weight/cost for the link attached to this port
   */
  private void processUpdate(short portNumber, short newWeight) {

  }

  /**
   * send an application-level message from this router to the destination router.
   * The message must be forwarded hop-by-hop according to the current shortest path.
   * <p/>
   * When you run send, the window of the router where you run the command should print:
   * "Sending message to <Destination IP>"
   * <p/>
   * For each intermediate router on the shortest path (excluding the source and destination),
   * the router window should print:
   * "Forwarding packet from <Source IP> to <Destination IP>"
   * <p/>
   * When the destination router receives the message, the router window should print:
   * "Received message from <Source IP>:"
   * "<Message>"
   *
   * @param destinationIP the simulated IP address of the destination router
   * @param message the message content to send
   */
  private void processSend(String destinationIP, String message) {

  }

  /**
   * handle incoming application message packet.
   * This method should be called when a router receives a SOSPFPacket with sospfType = 2 (Application Message).
   * <p/>
   * If this router is the destination (packet.dstIP equals this router's simulatedIPAddress):
   * - Print "Received message from <Source IP>:"
   * - Print the message content
   * <p/>
   * If this router is an intermediate router:
   * - Print "Forwarding packet from <Source IP> to <Destination IP>"
   * - Forward the packet to the next hop on the shortest path to the destination
   * - Do NOT print or inspect the message payload
   *
   * @param packet the received application message packet
   */
  private void handleApplicationMessage(SOSPFPacket packet) {

  }

  public void terminal() {
    try {
      System.out.print(">> ");
      String command;
      while (true) {
        command = consoleReader.readLine();
        if (command == null) break;
        command = command.trim();

        // Handle interactive attach confirmation if one is pending
        synchronized (attachLock) {
          if (hasPendingAttachRequest) {
            attachRequestAnswer = command.equalsIgnoreCase("Y");
            attachLock.notify();
            System.out.print(">> ");
            continue;
          }
        }

        if (command.isEmpty()) {
          System.out.print(">> ");
          continue;
        }

        try {
          if (command.startsWith("detect ")) {
            String[] cmdLine = command.split(" ");
            processDetect(cmdLine[1]);
          } else if (command.startsWith("disconnect ")) {
            String[] cmdLine = command.split(" ");
            processDisconnect(Short.parseShort(cmdLine[1]));
          } else if (command.startsWith("quit")) {
            processQuit();
          } else if (command.startsWith("attach ")) {
            String[] cmdLine = command.split(" ");
            processAttach(cmdLine[1], Integer.parseInt(cmdLine[2]),
                    cmdLine[3], Short.parseShort(cmdLine[4]));
          } else if (command.equals("start")) {
            processStart();
          } else if (command.startsWith("connect ")) {
            String[] cmdLine = command.split(" ");
            processConnect(cmdLine[1], Integer.parseInt(cmdLine[2]),
                    cmdLine[3], Short.parseShort(cmdLine[4]));
          } else if (command.equals("neighbors")) {
            processNeighbors();
          } else if (command.startsWith("send ")) {
            String[] cmdLine = command.split(" ", 3);
            if (cmdLine.length >= 3) {
              processSend(cmdLine[1], cmdLine[2]);
            } else {
              System.out.println("Usage: send [Destination IP] [Message]");
            }
          } else if (command.startsWith("update ")) {
            String[] cmdLine = command.split(" ");
            if (cmdLine.length >= 3) {
              processUpdate(Short.parseShort(cmdLine[1]), Short.parseShort(cmdLine[2]));
            } else {
              System.out.println("Usage: update [port_number] [new_weight]");
            }
          } else {
            System.out.println("Unknown command: " + command);
          }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
          System.out.println("Invalid arguments. Check command usage.");
        }
        System.out.print(">> ");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
