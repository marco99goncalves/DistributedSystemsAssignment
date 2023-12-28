# Distributed Systems Assignment

This project implements three different algorithms for distributed systems: Token Ring, Anti-Entropy and Totally Ordered Multicast. This readme will guide you trough the usage of the programs.

Before running any code, place yourself in the directorys `DistributedSystemsAsignment`

# Basic usage of the Peer class

Whenever you run a given Peer (regardless of the algorithm used), the common sintax is as follows:

`java ds.assign.[ring/entropy/chat].Peer [Current Peer] [Peers to connect]`

For example, an example would be:
`java ds.assign.entropy.Peer m3 m1 m4 m5`

The above command would run the `Anti-Entropy` algorithm, and it would create peer `m3` and connect it to peers `m1, m4 and m5`.

### Property files

Each algorithm has different property files stored on the `DistributedSystemsAsignment` directory. These files dictate the variables used in the program, and is, most importantly, used for setting the ports of the servers/peers. By default, Servers are located in `localhost` at port `40000` and peers are located in `localhost` starting in port `40001`.

### ⚠️ Entropy and Totally Ordered Multicast ⚠️

**Before starting each peer, make sure that you run all the commands to start the peers within the given `TIME_TO_WAIT` time window that is set in the correct `conf_*.prop` file. This tells the Peers to wait a given time (in milliseconds) before trying to start the process. If a targeted peer is not running, it will crash.**

# Token Ring

The token ring files are stored in `ds/assign/ring`. To compile them, run:

```
javac ds/assign/ring/Peer.java
javac ds/examples/sockets/calculatormulti/Server.java
javac ServerInjector.java
```

After running this, we need to start the main server, by running the command:

`java ds.examples.sockets.calculatormulti.Server [SERVER_HOST] [SERVER_PORT]` replacing `[SERVER_HOST]` and `[SERVER_PORT]` with the values you set in the `conf_ring.prop` file.

Now that the server is initiated, we start the Peers. For this, create a new terminal for each peer you want to run, and then, on each window, run the following command:

`java ds.assign.ring.Peer [Current Peer] [Next Peer]`

Here is an example for a ring of 5 peers:

```
[TERMINAL 1] java ds.assign.ring.Peer m1 m2
[TERMINAL 2] java ds.assign.ring.Peer m2 m3
[TERMINAL 3] java ds.assign.ring.Peer m3 m4
[TERMINAL 4] java ds.assign.ring.Peer m4 m5
[TERMINAL 5] java ds.assign.ring.Peer m5 m1
```

After running the command, each peer is ready to start passing the token, for this, run the following command:

`java ServerInjector [Target Peer Host] [Target Peer Port]`

This sends a token request to the peer at the given location, and starts the process.

# Anti-Entropy

The entropy files are stored in `ds/assign/entropy`. To compile them, run:

`javac ds/assign/entropy/Peer.java`

After compiling, we're ready to start the peers.

For this, create a new terminal for each peer you want to run, and then, on each window, run the following command:

`java ds.assign.entropy.Peer [Current Peer] [Target Peers]`

Here is an example for a ring of 4 peers:

```
[TERMINAL 1] java ds.assign.entropy.Peer m1 m2
[TERMINAL 2] java ds.assign.entropy.Peer m2 m1 m3 m4
[TERMINAL 3] java ds.assign.entropy.Peer m3 m2
[TERMINAL 4] java ds.assign.entropy.Peer m4 m2 m5 m6
[TERMINAL 5] java ds.assign.entropy.Peer m5 m4
[TERMINAL 6] java ds.assign.entropy.Peer m6 m4
```

# Totally Ordered Multicast (TOM)

The tom files are stored in `ds/assign/chat`. To compile them, run:

`javac ds/assign/chat/Peer.java`

After compiling, we're ready to start the peers.

For this, create a new terminal for each peer you want to run, and then, on each window, run the following command:

`java ds.assign.chat.Peer [Current Peer] [Target Peers]`

Here is an example for a ring of 4 peers:

```
[TERMINAL 1] java ds.assign.chat.Peer m1 m2 m3 m4 m5 m6
[TERMINAL 2] java ds.assign.chat.Peer m2 m1 m3 m4 m5 m6
[TERMINAL 3] java ds.assign.chat.Peer m3 m2 m1 m4 m5 m6
[TERMINAL 4] java ds.assign.chat.Peer m4 m2 m3 m1 m5 m6
[TERMINAL 5] java ds.assign.chat.Peer m5 m2 m3 m4 m1 m6
[TERMINAL 6] java ds.assign.chat.Peer m6 m2 m3 m4 m5 m1
```
