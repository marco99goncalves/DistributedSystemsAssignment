package ds.assign.entropy;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

import org.json.simple.JSONObject;
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

    public static int WORD_LAMBDA;
    public static int PUSH_PULL_LAMBDA;
    public static String WORD_FILE;
    public static ArrayList<Pair<String, Integer>> TARGETS;
    public static HashMap<String, Pair<String, Integer>> MACHINE_TO_IP;
    public static ArrayList<String> WORDS_ARRAY;
    public static TreeSet<String> words;
    public static ReentrantLock word_lock;
    public static int SEED;
    public static int TIME_TO_WAIT;
    public static int CONN_COUNT;
    public static String host;
    public static int port;
    Logger logger;

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

        // Retrieving values
        int MACHINES = Integer.parseInt(prop.getProperty("MACHINES"));
        for (int i = 1; i <= MACHINES; i++) {
            String machine = "m" + i;
            String machine_host = prop.getProperty("MACHINES." + machine + ".HOST");
            int machine_port = Integer.parseInt(prop.getProperty("MACHINES." + machine + ".PORT"));
            MACHINE_TO_IP.put(machine, new Pair<String, Integer>(machine_host, machine_port));
        }

        WORD_LAMBDA = Integer.parseInt(prop.getProperty("WORD_LAMBDA"));
        PUSH_PULL_LAMBDA = Integer.parseInt(prop.getProperty("PUSH_PULL_LAMBDA"));
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

        for (String s : words) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) throws Exception {
        Peer peer = new Peer(args[0]);
        System.out.printf("new peer @ host=%s\n", args[0]);

        ReadConfigurationFileProp("conf_entropy.prop");

        // Setup the connections
        TARGETS = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            TARGETS.add(MACHINE_TO_IP.get(args[i]));
        }

        CONN_COUNT = 0;

        host = MACHINE_TO_IP.get(args[0]).getKey();
        port = MACHINE_TO_IP.get(args[0]).getValue();

        word_lock = new ReentrantLock();
        words = new TreeSet<>();
        WORDS_ARRAY = new ArrayList<>();
        SetupWords();

        new Thread(new Server(host, port, peer.logger)).start();

        Thread.sleep(TIME_TO_WAIT);

        SEED = new Random().nextInt();
        new Thread(new WordGenerator(WORD_LAMBDA, SEED)).start();
        new Thread(new PushPullGenerator(PUSH_PULL_LAMBDA, SEED)).start();
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
        Peer.CONN_COUNT++;
    }

    @Override
    public void run() {
        try {
            /*
             * prepare socket I/O channels
             */
            InputStream inputStream = clientSocket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

            /*
             * Do stuff with the connection here, for now, nothing though
             */
            TreeSet<String> set_gotten = (TreeSet<String>) objectInputStream.readObject();

            Peer.word_lock.lock();
            try {
                Peer.words.addAll(set_gotten);

                OutputStream outputStream = clientSocket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);

                objectOutputStream.writeObject(Peer.words);
            } finally {
                System.out.println("\n==========\n");
                Peer.words.forEach(s -> System.out.print(s + " "));
                System.out.println("\n==========\n");

                Peer.word_lock.unlock();
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
 * Class responsible for generating the words each peers is sending. It works
 * via a Poisson generator.
 */
class WordGenerator implements Runnable {
    PoissonProcess pp;
    int seed;
    Random random;

    public WordGenerator(int lambda, int seed) {
        this.seed = seed;
        pp = new PoissonProcess(lambda, new Random(seed));
        this.random = new Random(seed);
    }

    @Override
    public void run() {
        while (true) {
            String word = /*Peer.port%10 + */Peer.WORDS_ARRAY.get(random.nextInt(Peer.WORDS_ARRAY.size()));
            Peer.word_lock.lock();
            try {
                //System.out.println("Generated: " + word);
                Peer.words.add(word);
            } finally {
                Peer.word_lock.unlock();
            }

            double t_sleep = pp.timeForNextEvent() * 60 * 1000;
            try {
                Thread.sleep((long) t_sleep);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

/**
 * Represents the class that randomly generates the PushPull requests
 */
class PushPullGenerator implements Runnable {

    PoissonProcess pp;
    int seed;
    Random random;

    public PushPullGenerator(int lambda, int seed) {
        this.seed = seed;
        random = new Random(seed);
        pp = new PoissonProcess(lambda, new Random(seed));
    }

    public int RandomInRange(Random random, int min, int max) {
        return (min + random.nextInt(max - min + 1));
    }

    @Override
    public void run() {
        while (true) {
            Pair<String, Integer> target = Peer.TARGETS.get(RandomInRange(random, 0, Peer.TARGETS.size() - 1));

            try {
                //System.out.println("PushPull with: " + target.getValue()%10);
                Socket peerSocket = new Socket(InetAddress.getByName(target.getKey()), target.getValue());

                OutputStream outputStream = peerSocket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);

                Peer.word_lock.lock();
                try {
                    objectOutputStream.writeObject(Peer.words);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Peer.word_lock.unlock();
                }

                InputStream inputStream = peerSocket.getInputStream();
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                TreeSet<String> set_gotten = (TreeSet<String>) objectInputStream.readObject();

                Peer.word_lock.lock();
                Peer.words.addAll(set_gotten);


                peerSocket.close();

                System.out.println("\n==========\n");
                Peer.words.forEach(s -> System.out.print(s + " "));
                System.out.println("\n==========\n");

                Peer.word_lock.unlock();

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            double t_sleep = pp.timeForNextEvent() * 60 * 1000;
            try {
                Thread.sleep((long) t_sleep);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
