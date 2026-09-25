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

## PA1 Commands

```
attach [Process IP] [Process Port] [Simulated IP] [Link Weight]
start
neighbors
```

* `attach` opens a socket to the target router and requests a link; the receiving router is
  prompted `(Y/N)` to accept or reject. Does **not** trigger HELLO/state sync by itself.
* `start` begins the HELLO handshake **only on links belonging to the router it's run on**
  (not network-wide). To fully converge a multi-router topology, run `start` on every router
  (or at least one endpoint of every link).
* `neighbors` lists simulated IPs of routers currently in `TWO_WAY` status, which are attached
  **and** handshake-complete.

Each router supports up to 4 links and further `attach` attempts are rejected once
full, on both the initiating and accepting side.

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

* Only `attach`, `start`  and `neighbors` are implemented
  for this assignment; `detect`, `send`, `connect`, `disconnect`, `update`, and `quit` are
  intentionally empty stubs.
* Each `Link` keeps its socket connection open after a successful `attach` rather than closing
  it, and a background thread listens on it. This is what lets `start`'s HELLO exchange
  happen later over the same connection.
