package ds.assign.ring;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.LoggingPermission;
import java.util.logging.SimpleFormatter;

import poisson.*;

/**
 * @param <T> First element of the pair
 * @param <E> Second element of the pair
 */
class Pair<T, E> {
    public T key;
    public E value;

    public Pair(T key, E value) {
        this.key = key;
        this.value = value;
    }

    public T getKey() {
        return key;
    }

    public void setKey(T key) {
        this.key = key;
    }

    public E getValue() {
        return value;
    }

    public void setValue(E value) {
        this.value = value;
    }
}

/**
 * Represents a Peer node in a Peer to Peer network
 */
public class Peer {
    public static String SERVER_HOST = "localhost";
    public static int SERVER_PORT = 40000;

    public static String TARGET_HOST;
    public static int TARGET_PORT;

    public static HashMap<String, Pair<String, Integer>> MACHINE_TO_IP;

    public static int OPERATION_LAMBDA = 0;
    String host;
    Logger logger;

    static volatile boolean hasToken = false;
    static ConcurrentLinkedQueue<String> serverOperations = new ConcurrentLinkedQueue<>();

    public Peer(String hostname) {
        host = hostname;
        logger = Logger.getLogger("logfile");

        try {
            FileHandler handler = new FileHandler("./LOGS/" + hostname + "_peer.log", true);
            logger.addHandler(handler);
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param file The file containing the properties
     * @throws IOException Throws an IOException whenever it can't read a file
     *                     and/or a property
     */
    public static void ReadConfigurationFileProp(String file) throws IOException {
        Properties prop = new Properties();
        MACHINE_TO_IP = new HashMap<>();
        prop.load(new FileInputStream(file));

        SERVER_HOST = prop.getProperty("SERVER_HOST");
        SERVER_PORT = Integer.parseInt(prop.getProperty("SERVER_PORT"));

        // Retrieving values
        int MACHINES = Integer.parseInt(prop.getProperty("MACHINES"));
        for (int i = 1; i <= MACHINES; i++) {
            String machine = "m" + i;
            String machine_host = prop.getProperty("MACHINES." + machine + ".HOST");
            int machine_port = Integer.parseInt(prop.getProperty("MACHINES." + machine + ".PORT"));
            MACHINE_TO_IP.put(machine, new Pair<String, Integer>(machine_host, machine_port));
        }

        OPERATION_LAMBDA = Integer.parseInt(prop.getProperty("OPERATION_LAMBDA"));
    }

    public static void main(String[] args) throws Exception {
        Peer peer = new Peer(args[0]);
        System.out.printf("new peer @ host=%s\n", args[0]);

        ReadConfigurationFileProp("conf_ring.prop");

        String host = MACHINE_TO_IP.get(args[0]).getKey();
        int port = MACHINE_TO_IP.get(args[0]).getValue();

        TARGET_HOST = MACHINE_TO_IP.get(args[1]).getKey();
        TARGET_PORT = MACHINE_TO_IP.get(args[1]).getValue();

        new Thread(new Server(host, port, peer.logger)).start();
        new Thread(new Client(peer.logger, OPERATION_LAMBDA)).start();
    }
}

/**
 * Server used for receiving connections from other peers
 */
class Server implements Runnable {
    String host;
    int port;
    ServerSocket server;
    Logger logger;

    public Server(String host, int port, Logger logger) throws Exception {
        this.host = host;
        this.port = port;
        this.logger = logger;
        server = new ServerSocket(port);
    }

    @Override
    public void run() {
        try {
            logger.info("server: endpoint running at port " + port + " ...");
            while (true) {
                try {
                    Socket client = server.accept();
                    String clientAddress = client.getInetAddress().getHostAddress();

                    new Thread(new Connection(clientAddress, client, logger)).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Function that sends an operation to the server, and receives it's result
     * 
     * @param operation Operation to be sent to the server
     */
    public static void SendReceiveMessage(String operation) {
        try {
            /*
             * prepare socket I/O channels
             */
            Socket serverSocket = new Socket(InetAddress.getByName(Peer.SERVER_HOST), Peer.SERVER_PORT);
            PrintWriter out = new PrintWriter(serverSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            /*
             * send command
             */
            System.out.printf(operation);
            out.println(operation);
            out.flush();
            /*
             * receive result
             */
            String result = in.readLine();
            System.out.printf("= %.3f\n", Double.parseDouble(result));
            /*
             * close connection
             */
            out.close();
            in.close();
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * Represents a connection with another peer. This class is called whenever
 * another peer connects to us via the Server class
 */
class Connection implements Runnable {
    String clientAddress;
    Socket clientSocket;
    Logger logger;

    public Connection(String clientAddress, Socket clientSocket, Logger logger) {
        this.clientAddress = clientAddress;
        this.clientSocket = clientSocket;
        this.logger = logger;
    }

    @Override
    public void run() {
        /*
         * prepare socket I/O channels
         */
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String command;
            command = in.readLine();

            /*
             * We got a message from the peer, so we have the token now
             */
            if (command != null) {
                System.out.println("Got token, sending " + Peer.serverOperations.size() + " things to server");

                while (!Peer.serverOperations.isEmpty()) {
                    String op = Peer.serverOperations.poll();
                    Server.SendReceiveMessage(op);
                }
                Thread.sleep(1 * 1000); // Useless, we're just doing this so it's easier to see the token passing trough
                                        // the peers

                Socket peerSocket = new Socket(InetAddress.getByName(Peer.TARGET_HOST), Peer.TARGET_PORT);
                System.out.println("Sending token");
                PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);
                peerOut.println("");
                peerOut.flush();
                peerOut.close();

                /*
                 * close connection
                 */
                peerSocket.close();
            }

            /*
             * close connection
             */
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * Client class used to represent input from the user. It's currently useless,
 * but it's a good aproximation on how the user would interact with the programs
 */
class Client implements Runnable {
    Logger logger;

    public static int OPERATION_LAMBDA; // Events per minute

    public Client(Logger logger, int OPERATION_LAMBDA) throws Exception {
        this.logger = logger;
        this.OPERATION_LAMBDA = OPERATION_LAMBDA;
    }

    @Override
    public void run() {
        /**
         * This is useless right now, but in a future implementation could be populated
         * Right now the Client class just exists to spawn a thread with the Poisson
         * Generator
         * This could be implemented differently, for example, the poisson generator
         * thread could be spawned in the main function
         * but we're assuming that each operation generated is meant to simulate user
         * input, therefore, a client class is simulated
         */

        new Thread(new PoissonSimulation(OPERATION_LAMBDA)).start();
    }
}

/**
 * Class that randomly generates the operations
 */
class PoissonSimulation implements Runnable {

    PoissonProcess pp;

    private static final double MIN = 0;
    private static final double MAX = 100;

    public PoissonSimulation(int lambda) {
        pp = new PoissonProcess(lambda, new Random());
    }

    public double randomInRange() {
        Random random = new Random();
        double range = MAX - MIN;
        double scaled = random.nextDouble() * range;
        double shifted = scaled + MIN;
        return shifted;
    }

    public String randomOperation() {
        int op = new Random().nextInt(4);
        String ans = "";
        switch (op) {
            case 0:
                ans = "add";
                break;
            case 1:
                ans = "sub";
                break;
            case 2:
                ans = "mul";
                break;
            case 3:
                ans = "div";
                break;
        }

        return ans;
    }

    @Override
    public void run() {
        while (true) {
            String op = randomOperation();
            String a = String.valueOf(randomInRange());
            String b = String.valueOf(randomInRange());

            Peer.serverOperations.add(String.format("%s:%.4s:%.4s", op, a, b));

            double t_sleep = pp.timeForNextEvent() * 60 * 1000;
            try {
                Thread.sleep((long) t_sleep);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
