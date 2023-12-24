import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class ServerInjector {

    public static void main(String[] args) throws IOException {
        try {
            System.out.println(args[0]);
            System.out.println(args[1]);

            Socket firstMachineSocket = new Socket(InetAddress.getByName(args[0]), Integer.valueOf(args[1]));

            PrintWriter out = new PrintWriter(firstMachineSocket.getOutputStream(), true);
            out.println("");
            out.flush();
            Thread.sleep(1 * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
