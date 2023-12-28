package ds.assign.chat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import poisson.PoissonProcess;

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
 * Represents a Lamport Message
 */
class LamportMessage implements Comparable<LamportMessage>, Serializable {
    int timestamp;
    String message;
    boolean isAck;
    int process;

    public LamportMessage(int timestamp, String message, boolean isAck, int process) {
        this.timestamp = timestamp;
        this.message = message;
        this.isAck = isAck;
        this.process = process;
    }

    @Override
    public int compareTo(LamportMessage other) {
        if (this.timestamp == other.timestamp)
            return Integer.compare(this.process, other.process);
        return Integer.compare(this.timestamp, other.timestamp);
    }
}

/**
 * Represents a Peer node in a Peer to Peer network
 */
public class Peer {
    public static int WORD_LAMBDA; // How many words we generate (on average) per minute
    public static String WORD_FILE; // File with the words we want to print
    public static ArrayList<String> WORDS_ARRAY; // Represents the same file as WORD_FILE, however, it's we can access a
    // random word in O(1)
    public static ArrayList<Pair<String, Integer>> TARGETS; // Machines we are currently connected to
    public static HashMap<String, Pair<String, Integer>> MACHINE_TO_IP; // Dictionary to translate the machine name to a
    // pair <HOST, PORT>, for example: m1:
    // {"localhost", 4001}

    public static ConcurrentSkipListSet<LamportMessage> MESSAGE_QUEUE; // Messages received from other peers to be used
    // in the totally ordered multicast algorithm

    public static AtomicInteger CLOCK; // Lamport Clock
    public static String HOST; // Our current host name
    public static int PORT; // Our current port number
    public static int TIME_TO_WAIT;

    Logger logger;

    public Peer(String hostname) {
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

        // Retrieving values
        int MACHINES = Integer.parseInt(prop.getProperty("MACHINES"));
        for (int i = 1; i <= MACHINES; i++) {
            String machine = "m" + i;
            String machine_host = prop.getProperty("MACHINES." + machine + ".HOST");
            int machine_port = Integer.parseInt(prop.getProperty("MACHINES." + machine + ".PORT"));
            MACHINE_TO_IP.put(machine, new Pair<String, Integer>(machine_host, machine_port));
        }

        WORD_LAMBDA = Integer.parseInt(prop.getProperty("WORD_LAMBDA"));
        WORD_FILE = prop.getProperty("WORD_FILE");
        TIME_TO_WAIT = Integer.parseInt(prop.getProperty("TIME_TO_WAIT"));
    }

    /**
     * Reads the file stored in WORD_FILE and then places its trimmed contents into
     * the WORDS_ARRAY
     */
    public static void SetupWords() {
        try (BufferedReader br = new BufferedReader(new FileReader(WORD_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                WORDS_ARRAY.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Peer peer = new Peer(args[0]);
        ReadConfigurationFileProp("conf_chat.prop");

        System.out.println(WORD_LAMBDA);

        // Setup the connections

        TARGETS = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            TARGETS.add(MACHINE_TO_IP.get(args[i]));
        }

        WORDS_ARRAY = new ArrayList<>();
        SetupWords();

        // QUEUE_LOCK = new ReentrantLock();

        Pair<String, Integer> p = MACHINE_TO_IP.get(args[0]);
        HOST = p.getKey();
        PORT = p.getValue();

        CLOCK = new AtomicInteger(PORT % 10);

        System.out.printf("new peer @ host=%s\n", args[0]);

        MESSAGE_QUEUE = new ConcurrentSkipListSet<>();

        new Thread(new Server(HOST, PORT, peer.logger)).start();

        Thread.sleep(TIME_TO_WAIT);

        new Thread(new WordGenerator(WORD_LAMBDA)).start();
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
     * @param message Message to be sent to all the peers connected to this node
     */
    public static void BroadcastToConnectedPeers(LamportMessage message) throws Exception {
        for (Pair<String, Integer> target : Peer.TARGETS) {
            Socket peerSocket = new Socket(InetAddress.getByName(target.getKey()), target.getValue());

            OutputStream outputStream = peerSocket.getOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(message);
            peerSocket.close();
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
        try {
            InputStream inputStream = clientSocket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

            LamportMessage message_gotten = (LamportMessage) objectInputStream.readObject();
            clientSocket.close();

            // Receive the message, and check if it's an ack or not
            Peer.CLOCK.set(Math.max(Peer.CLOCK.get(), message_gotten.timestamp) + 1);
            if (!message_gotten.isAck) {
                LamportMessage m = new LamportMessage(Peer.CLOCK.get(), "", true, Peer.PORT);
                Server.BroadcastToConnectedPeers(m);
            }
            Peer.MESSAGE_QUEUE.add(message_gotten);

            // Check if we can print the message or not
            if (ExistsMessageFromAllPeers()) {
                LamportMessage message = Peer.MESSAGE_QUEUE.pollFirst();
                if (!message.isAck) {
                    Peer.CLOCK.incrementAndGet();
                    System.out.println(message.message);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean ExistsMessageFromAllPeers() {
        HashSet<Integer> counts = new HashSet<>();
        for (LamportMessage message : Peer.MESSAGE_QUEUE) {
            counts.add(message.process);
            if (counts.size() >= Peer.TARGETS.size())
                return true;
        }

        return false;
    }

}

/**
 * Class responsible for generating the words each peers is sending. It works
 * via a Poisson generator.
 */
class WordGenerator implements Runnable {
    PoissonProcess poissonProcess;
    Random random;

    public WordGenerator(int lambda) {
        poissonProcess = new PoissonProcess(lambda, new Random());
        this.random = new Random();
    }

    @Override
    public void run() {
        while (true) {
            String word = Peer.WORDS_ARRAY.get(random.nextInt(Peer.WORDS_ARRAY.size()));

            // Send message with the word, including to ourselves
            Peer.CLOCK.incrementAndGet();
            LamportMessage m = new LamportMessage(Peer.CLOCK.get(), word, false, Peer.PORT);

            // Broadcast the message
            try {
                Server.BroadcastToConnectedPeers(m);
            } catch (Exception e) {
                e.printStackTrace();
            }

            double t_sleep = poissonProcess.timeForNextEvent() * 60 * 1000;
            try {
                Thread.sleep((long) t_sleep);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
