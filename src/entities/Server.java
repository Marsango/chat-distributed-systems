package entities;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Server {
    private ServerSocket connection;
    private static final ArrayList<ClientConnection> clientList = new ArrayList<>();

    public Server(int port, int queueSize) throws IOException {
        this.connection = new ServerSocket(port, queueSize);

    }

    public void acceptNewConnections() throws IOException {
        while (true) {
            new ClientConnection(this.connection.accept(), this).start();
        }
    }

    public void connectionClosed(Socket socket) {
        System.out.println("A conexão " + socket + "foi fechada!");
    }

    public void connectionOpened(Socket socket) {
        System.out.println("A conexão " + socket + "foi aberta!");
    }

    public void showActiveConnections() {
        System.out.println("Conexões ativas:\n");
        for (ClientConnection connection: clientList){
            System.out.println("Usuário: " + connection.getUserName() + "Conexão: " + connection.getSocket());
        }
    }


    public static void main(String[] args) throws IOException {
        Server server = new Server(8501, 40);
        server.acceptNewConnections();
    }
}
