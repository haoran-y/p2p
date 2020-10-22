import java.io.*;
import java.net.*;
import java.util.*;

public class chat_server implements Runnable
{
    // Each instance has a separate socket
    private Socket clientSock;

    // Whole class keeps track of active clients
    private static Map<chat_server, Boolean> clientList;

    private String name;

    private chat_server paired;

    private BufferedReader fromClientReader;
    private PrintWriter toClientWriter;

    // Constructor sets the socket for the child thread to process
    public chat_server(Socket sock) throws IOException {
        clientSock = sock;
        fromClientReader = new BufferedReader(
                new InputStreamReader(clientSock.getInputStream()));
        toClientWriter =
                new PrintWriter(clientSock.getOutputStream(), true);
        paired = null;
    }

    // Add the given client to the active clients list
    // Since all threads share this, we use "synchronized" to make it atomic
    public static synchronized void addClient(chat_server server)
    {
        clientList.put(server, true);
    }

    // Remove the given client from the active clients list
    // Since all threads share this, we use "synchronized" to make it atomic
    public static synchronized void removeClient(chat_server server)
    {
        clientList.remove(server);
    }

    public String getName() {
        return name;
    }

    public String getClients() {
        String output = "Lists of clients and states\n";
        for (chat_server i : clientList.keySet()) {
            output += i.getName() + "           ";
            if (clientList.get(i)) {
                output += "free\n";
            } else {
                output += "busy\n";
            }
        }
        return output;
    }

    public synchronized void update(String output) {
        for (chat_server i: clientList.keySet()) {
            if (clientList.get(i)) {
                i.toClientWriter.println(output);
            }
        }
    }

    public synchronized boolean checkAvail() {
        int avail = 0;
        for (boolean i : clientList.values()) {
            if (i) {
                avail ++;
            }
        }
        return avail >= 2;
    }

    public synchronized boolean confirm (chat_server i) throws IOException {
        i.toClientWriter.println("Received request from " + name + "\nConnect?");
        String answer = i.fromClientReader.readLine();
        return answer.toLowerCase().equals("y") || answer.toLowerCase().equals("yes");
    }

    public synchronized void pair() throws IOException {
        toClientWriter.println("Which client to connect to?");
        whileloop:
        while (paired == null) {
            if (fromClientReader.ready()) {
                String pairedName = fromClientReader.readLine();
                for (chat_server i : clientList.keySet()) {
                    if (i.getName().equals(pairedName)) {
                        if (clientList.get(i)) {
                            if (confirm(i)) {
                                clientList.replace(i, false);
                                clientList.replace(this, false);
                                paired = i;
                                i.paired = this;
                                toClientWriter.println("You are connected to " + i.getName());
                                i.toClientWriter.println("You are connected to " + name);
                            } else {
                                toClientWriter.println(i.name + " declined the connection.");
                                pair();
                            }
                        } else {
                            toClientWriter.println("Denied");
                            pair();
                        }
                        break whileloop;
                    }
                }
                toClientWriter.println("Name not found!");
                pair();
                break;
            }
        }
    }

    public boolean checkName(String name) {
        for (chat_server i : clientList.keySet()) {
            if (i.getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    public synchronized void disconnect() throws IOException {
        paired = null;
        clientList.replace(this, true);
        System.out.println(getClients());
        update(getClients());
        fromClientReader = new BufferedReader(
                new InputStreamReader(clientSock.getInputStream()));
        pair();
    }

    // The child thread starts here
    public void run()
    {
        // Read from the client and relay to other clients
        try {
            System.out.println("Client accepted");

            while (true) {
                toClientWriter.println("Please enter your name:");
                // Get the client name
                name = fromClientReader.readLine();
                if (checkName(name)) {
                    break;
                } else {
                    toClientWriter.println("Name already taken.");
                }
            }

            // Add this client to the active client list
            addClient(this);

            System.out.println(getClients());
            System.out.println("Waiting for a client ...");
            update(getClients());

            pair();

            // Keep doing till client sends EOF
            while (true) {
                String line = fromClientReader.readLine();
                if (line == null) {
                    removeClient(this);
                    if (paired != null) {
                        paired.toClientWriter.println(name + " disconnected");
                        paired.disconnect();
                    }
                    break;
                }
                paired.toClientWriter.println(name + ": " + line);
            }

            // Done with the client, close everything
            toClientWriter.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /*
     * The conf server program starts from here.
     * This main thread accepts new clients and spawns a thread for each client
     * Each child thread does the stuff under the run() method
     */
    public static void main(String args[])
    {
        // Server needs a port to listen on
        if (args.length != 1) {
            System.out.println("usage: java ConfServer <port>");
            System.exit(1);
        }

        // Be prepared to catch socket related exceptions
        try {
            // Create a server socket with the given port
            ServerSocket serverSock =
                    new ServerSocket(Integer.parseInt(args[0]));

            // Keep track of active clients
            clientList = new HashMap<>();
            System.out.println("Waiting for a client ...");
            // Keep accepting/serving new clients
            while (true) {
                // Wait for another client
                Socket clientSock = serverSock.accept();
                // Spawn a thread to read/relay messages from this client
                Thread child = new Thread(new chat_server(clientSock));
                child.start();
            }
        } catch(Exception e) {
            System.out.println(e);
        }
    }
}