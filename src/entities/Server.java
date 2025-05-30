package entities;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Server {
    private ServerSocket connection;
    private Socket socket;

    public Server(int port, int queueSize) throws IOException {
        this.connection = new ServerSocket(port, queueSize);

    }

    public void acceptNewConnections() throws IOException {
        while (true) {
            new ClientConnection(this.connection.accept());
        }
    }


    public static void main(String[] args) throws IOException {
        Server server = new Server(8501, 40);
        server.acceptNewConnections();
    }
}
