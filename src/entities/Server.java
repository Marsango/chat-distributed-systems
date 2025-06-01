package entities;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private ServerSocket serverSocket;

    public Server(int port, int queueSize) throws IOException {
        this.serverSocket = new ServerSocket(port, queueSize);
        System.out.println("Servidor iniciado na porta " + port + ". Esperando por clientes...");
    }

    public void startServer() {
        try {
            while (!serverSocket.isClosed()) {
                // Aceitar conexões simultâneas de múltiplos clientes
                Socket clientSocket = this.serverSocket.accept();
                System.out.println("[SERVIDOR] Novo cliente conectado de " + clientSocket.getRemoteSocketAddress());

                // Cria uma nova thread para cada cliente
                ClientConnection clientHandler = new ClientConnection(clientSocket);
                clientHandler.start();
            }
        } catch (IOException e) {
            System.err.println("Erro no loop do servidor: " + e.getMessage());
            closeServerSocket();
        }
    }

    public void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Server socket fechado.");
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar server socket: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        try {
            Server server = new Server(8501, 40);
            server.startServer();
        } catch (IOException e){
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
}
