package entities;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Server {
    private ServerSocket connection;
    private Socket socket;
    private ArrayList<Client> clientList;

    public Server(int port, int queueSize) throws IOException {
        this.connection = new ServerSocket(port, queueSize);
        this.clientList = new ArrayList<Client>();
    }
}
