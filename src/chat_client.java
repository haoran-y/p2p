import java.net.*;
import java.io.*;
import java.util.Scanner;

public class chat_client implements Runnable
{
    // For reading messages from the keyboard
    private BufferedReader fromUserReader;

    // For writing messages to the socket
    private PrintWriter toSockWriter;

    // Constructor sets the reader and writer for the child thread
    public chat_client(BufferedReader reader, PrintWriter writer)
    {
        fromUserReader = reader;
        toSockWriter = writer;
    }

    // The child thread starts here
    public void run()
    {

        try {
            // Keep doing till user types EOF (Ctrl-D)
            while (true) {
                // Read a line from the user
                String line = fromUserReader.readLine();

                // If we get null, it means EOF, close socket
                if (line == null) {
                    toSockWriter.close();
                    break;
                }

                // Write the line to the socket
                toSockWriter.println(line);
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    /*
     * The conf client program starts from here.
     * It sets up streams for reading & writing from keyboard and socket
     * Spawns a thread which does the stuff under the run() method
     * Then, it continues to read from socket and write to display
     */
    public static void main(String args[])
    {
        // Client needs server's contact information and user name
        if (args.length != 2) {
            System.out.println("usage: java ConfClient <host> <port>");
            System.exit(1);
        }

        // Connect to the server at the given host and port
        Socket sock = null;
        try {
            sock = new Socket(args[0], Integer.parseInt(args[1]));
            System.out.println(
                    "Connected to server at " + args[0] + ":" + args[1]);
        }
        catch(Exception e) {
            System.out.println(e);
        }

        // Set up a thread to read from user and send to server
        try {
            // Prepare to write to socket with auto flush on
            PrintWriter toSockWriter =
                    new PrintWriter(sock.getOutputStream(), true);

            // Prepare to read from keyboard
            BufferedReader fromUserReader = new BufferedReader(
                    new InputStreamReader(System.in));

            // Spawn a thread to read from user and write to socket
            Thread child = new Thread(
                    new chat_client(fromUserReader, toSockWriter));
            child.start();
        }
        catch(Exception e) {
            System.out.println(e);
        }

        // Now read from server and display to user
        try {
            // Prepare to read from socket
            BufferedReader fromSockReader = new BufferedReader(
                    new InputStreamReader(sock.getInputStream()));

            // Keep doing till server is done
            while (true) {
                // Read a line from the socket
                String line = fromSockReader.readLine();

                // Check if we got EOF on socket
                if (line == null)
                    break;

                // Write the line to the user
                System.out.println(line);
            }
        }
        catch(SocketException e) {
            // Ignore potential socket closed exception
        }
        catch(Exception e) {
            System.out.println(e);
        }

        // Exit to stop the child thread
        System.exit(0);
    }
}