package ds.examples.sockets.calculatormulti;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private Scanner scanner;

    private Client(InetAddress serverAddress, int serverPort) throws Exception {
        this.socket = new Socket(serverAddress, serverPort);
        this.scanner = new Scanner(System.in);
    }

    /*
     * send messages such as:
     * - add:x:y
     * - sub:x:y
     * - mul:x:y
     * - div:x:y
     * where x, y are floting point values
     */

    private void start() throws IOException {
        String command;
        String result;

        /*
         * prepare socket I/O channels
         */
        PrintWriter out = new PrintWriter(this.socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

        while (true) {
            /*
             * read command
             */
            System.out.print("$ ");
            command = scanner.nextLine();
            if (command.equals("quit"))
                break;

            /*
             * send command
             */
            out.println(command);
            out.flush();

            /*
             * receive result
             */
            result = in.readLine();
            System.out.printf("result is: %f\n", Double.parseDouble(result));
        }
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
        System.out.printf("\r\nconnected to server: %s\n", client.socket.getInetAddress());
        client.start();
    }
}
