package entities;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Client {

    private Socket socket;
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;
    private String username;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.objOut = new ObjectOutputStream(socket.getOutputStream());
            this.objIn = new ObjectInputStream(socket.getInputStream());
            this.username = username;

            objOut.writeObject(username);
            objOut.flush();
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
            closeAll();
        }
    }

    public void sendMessageLoop() {
        Scanner scanner = new Scanner(System.in);
        try {
            System.out.println("Conectado ao servidor. Digite suas mensagens.");
            System.out.println("Digite '/privado <nomeUsuário> <mensagem>' para mensagens privadas.");
            System.out.println("Digite '/usuarios' para listar os usuários conectados.");
            System.out.println("Digite '/quit' para sair do chat.");


            while (socket.isConnected() && !socket.isClosed()) {
                String userInput = scanner.nextLine();
                Message messageToSend;

                if (userInput.trim().equalsIgnoreCase("/quit")) {
                    break; // Sai do loop para fechar a conexão
                } else if (userInput.trim().equalsIgnoreCase("/usuarios")) {
                    messageToSend = new Message(username, null, "/usuarios");
                } else if (userInput.toLowerCase().startsWith("/privado")) {
                    // Formato: /privado <destinatario> <mensagem>
                    String[] parts = userInput.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Formato inválido de mensagem privada. Use o formato: /privado <nomeUsuário> <mensagem>");
                        continue;
                    }
                    String recipient = parts[1];
                    String privateContent = parts[2];
                    messageToSend = new Message(username, recipient, "/privado:" + privateContent);
                } else {
                    // Mensagem de broadcast (destinatário é null)
                    messageToSend = new Message(username, null, userInput);
                }
                objOut.writeObject(messageToSend);
                objOut.flush();
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                System.err.println("Erro ao enviar mensagem: " + e.getMessage());
            }
        } finally {
            System.out.println("Fechando conexão...");
            closeAll();
            scanner.close();
        }
    }

    public void listenForMessages() {
        new Thread(() -> {
            try {
                while (socket.isConnected() && !socket.isClosed()) {
                    Message messageFromServer = (Message) objIn.readObject();
                    if (messageFromServer != null) {
                        // Requisito: Exibir mensagens com carimbo de data/hora e remetente
                        // Exemplo: [Data e Hora] <Remetente> -> <Destinatário>: Mensagem
                        String formattedTime = messageFromServer.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        String displayMessage;

                        if ("SERVIDOR".equals(messageFromServer.getSender())) {
                            displayMessage = String.format("[%s] %s",
                                    formattedTime,
                                    messageFromServer.getContent());
                        } else if (messageFromServer.getReceiver() != null && messageFromServer.getReceiver().equals(username)) { // Mensagem privada para mim
                            displayMessage = String.format("[%s] %s (privado de %s): %s",
                                    formattedTime,
                                    messageFromServer.getSender(),
                                    messageFromServer.getSender(),
                                    messageFromServer.getContent());
                        } else if (messageFromServer.getReceiver() != null) {
                            displayMessage = String.format("[%s] Você (para %s): %s",
                                    formattedTime,
                                    messageFromServer.getReceiver(),
                                    messageFromServer.getContent());
                        }
                        else { // Mensagem de broadcast
                            displayMessage = String.format("[%s] %s: %s",
                                    formattedTime,
                                    messageFromServer.getSender(),
                                    messageFromServer.getContent());
                        }
                        System.out.println(displayMessage);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (!socket.isClosed()) {
                    System.err.println("Desconectado do servidor ou erro ao escutar as mensagens: " + e.getMessage());
                }
            } finally {
                closeAll();
            }
        }).start();
    }

    public void closeAll() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (objIn != null) {
                objIn.close();
            }
            if (objOut != null) {
                objOut.close();
            }
        } catch (IOException e) {
            System.err.println("Erro durante o fechamento: " + e.getMessage());
        }
        System.out.println("Recursos de conexão fechados.");
    }


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Digite seu username para o chat em grupo: ");
        String username = scanner.nextLine();

        try {
            Socket socket = new Socket("127.0.0.1", 8501);
            Client client = new Client(socket, username);
            client.listenForMessages();
            client.sendMessageLoop();
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }
}