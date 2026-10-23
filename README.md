# COMP535 Project Starter Code

## Requirements

To compile and run this project, you need:

* Java: Version 8 or higher (JDK 8+)
* Maven: Version 3.0 or higher

### Checking Your Versions

```
java -version
mvn -version
```

If you don't have these installed, please install them before proceeding.

## Building the Project

To compile the project:

```
mvn clean compile
```

To compile and create a JAR file:

```
mvn clean package
```

The compiled classes will be in `target/classes/` directory.

## Running the Program

Create an executable JAR with dependencies and run it:

```
mvn clean package assembly:single
java -jar target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar conf/router1.conf
```

Run one instance per router, each in its own terminal, pointed at that router's own
`.conf` file. On startup, each router prints its Process IP, Process Port, and Simulated IP.
You'll need the Process IP/Port to `attach` to it from another terminal.

## Commands

```
attach [Process IP] [Process Port] [Simulated IP] [Link Weight]
start
neighbors
detect [Destination IP]
send [Destination IP] [Message]
stats
dump
```

### PA1

* `attach` opens a socket to the target router and requests a link; the receiving router is
  prompted `(Y/N)` to accept or reject. Does **not** trigger HELLO/state sync by itself.
* `start` begins the HELLO handshake **only on links belonging to the router it's run on**
  (not network-wide). To fully converge a multi-router topology, run `start` on every router
  (or at least one endpoint of every link).
* `neighbors` lists simulated IPs of routers currently in `TWO_WAY` status, which are attached
  **and** handshake-complete.

Each router supports up to 4 links and further `attach` attempts are rejected once
full, on both the initiating and accepting side.

### PA2

* `start` now also synchronizes the link state database. Whenever a link reaches `TWO_WAY`,
  the router rebuilds its own LSA (with an incremented sequence number) and floods its LSDB
  to its `TWO_WAY` neighbors in `LSAUPDATE` messages. A router that receives an LSAUPDATE only
  stores LSAs with a higher sequence number than the one it holds, and forwards only those
  newer LSAs to all neighbors except the one it received them from.
* `detect [Destination IP]` runs Dijkstra (using link weights, not hop count) over the LSDB and
  prints `Path found: <src> -> (cost) <hop> -> ... -> <dst>`, or `Path not found`.
* `send [Destination IP] [Message]` sends an application message hop by hop along the shortest
  path. Intermediate routers print the source, destination, and next hop but never the payload;
  only the destination prints the message.
* `stats` prints the number of HELLO, LSAUPDATE, and APP_MESSAGE messages sent and received since
  the router started. A message sent to several neighbors counts once per neighbor, and a
  forwarded application message counts as both received and sent. The HELLOs exchanged during
  `attach` are included in the HELLO counters.
* `dump` (debugging aid, not part of the spec) prints this router's current LSDB.

**Usage note:** flooding is asynchronous, so after running `start` on every router, give the
network a moment to converge (check with `dump`) before running `detect` or `send`.

## Troubleshooting

**"JAVA_HOME not set" error**

* Make sure Java is installed and JAVA_HOME environment variable points to your Java installation
* On Linux/Mac: `export JAVA_HOME=/path/to/java`
* On Windows: Set JAVA_HOME in System Environment Variables

**"Maven not found" error**

* Make sure Maven is installed and added to your PATH
* Verify with: `mvn -version`

**Compilation errors**

* Ensure you have Java 8 or higher installed
* Ensure you have Maven 3.0 or higher installed
* Try: `mvn clean compile` to rebuild from scratch

**"Could not find or load main class" error**

* Make sure you built the JAR file: `mvn clean package assembly:single`
* Verify the JAR exists: `ls target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar`
* Rebuild the project: `mvn clean package assembly:single`
* Use the correct command: `java -jar target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar conf/router1.conf`

**Maven settings.xml warning**

* If you see a warning about "Unrecognised tag: 'blocked'" in Maven settings.xml, you can safely ignore it
* This is a system-level Maven configuration issue and does not affect the build
* The build will still succeed despite this warning

## Project Structure

```
src/
  main/
    java/          # Source code
  resources/       # Configuration files
conf/              # Router configuration files
target/            # Compiled classes (generated)
```

## Implementation Notes

* `connect`, `disconnect`, `update`, and `quit` are intentionally empty stubs (PA3).
* Each `Link` keeps its socket connection open after a successful `attach` rather than closing
  it, and a background thread listens on it. HELLO, LSAUPDATE, and application messages all
  travel over that same connection.
* Each router's LSA lists one entry per `TWO_WAY` neighbor. Dijkstra skips the weight-0
  self-link every router's LSDB starts with.
* Link streams are reset (`ObjectOutputStream.reset()`) before every write, so updated LSAs are
  never replaced by stale cached copies.
* Restart the whole topology between test runs: LSA sequence numbers restart at 1 when a router
  process restarts, so neighbors would ignore a restarted router's new LSAs.
