package entities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class ClientConnection extends Thread {
    private Socket socket;
    private static ArrayList<ClientConnection> clientList = new ArrayList<>();
    private DataInputStream in;
    private DataOutputStream out;
    private String userName;

    private Server server;

    public ClientConnection(Socket socket, Server server){
        try {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.userName = in.readUTF();
            this.server = server;
            clientList.add(this);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void broadcastMessage(String message){
        for (ClientConnection clientConnection : clientList){
            try {
                clientConnection.out.writeUTF(message + '\n');
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void run(){
        String messageFromClient;
        while (socket.isConnected()) {
            try {
                messageFromClient = in.readUTF();
                broadcastMessage(messageFromClient);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
