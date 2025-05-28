package entities;

import java.net.Socket;

public class ClientConnection extends Thread {
    private Socket socket;

    public ClientConnection(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run(){
        
    }
}
