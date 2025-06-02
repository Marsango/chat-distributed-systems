package entities;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class ClientConnection extends Thread {
    private Socket socket;
    private static final List<ClientConnection> clientList = new CopyOnWriteArrayList<>();
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;
    private String userName;

    public ClientConnection(Socket socket){
        try {
            this.socket = socket;
            this.objOut = new ObjectOutputStream(socket.getOutputStream());
            this.objIn = new ObjectInputStream(socket.getInputStream());
            this.userName = (String) objIn.readObject();
            boolean userNameTaken = usernameAlreadyTaken();
            objOut.writeBoolean(userNameTaken);
            objOut.flush();
            if (userNameTaken) {
                return;
            }
            clientList.add(this);

            System.out.println("[SERVIDOR] " + userName + " conectado " + clientList.size());
            broadcastSystemMessage(userName + " entrou no chat", null);

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SERVIDOR] Erro ao conectar o cliente " + e.getMessage());
            closeConnection();
        }
    }

    public boolean usernameAlreadyTaken() {
        for (ClientConnection clientConnection : clientList) {
            if (clientConnection.userName.equals(this.userName)){
                return true;
            }
        }
        return false;
    }

    // Método para enviar Message objects
    public void sendMessageToClient(Message message) throws IOException {
        objOut.writeObject(message);
        objOut.flush(); // envia o objeto imediatamente
    }

    public void broadcastMessage(Message messageToBroadcast){
        for (ClientConnection clientConnection : clientList){
            if(!clientConnection.userName.equals(messageToBroadcast.getSender())){
                try {
                    clientConnection.sendMessageToClient(messageToBroadcast);
                } catch (IOException e) {
                    System.err.println("[SERVIDOR] Erro ao transmitir para " + clientConnection.userName + ": " + e.getMessage());
                }
            }
        }
    }

    public void broadcastSystemMessage(String conteudo, String destinatario){
        Message systemMessage = new Message("SERVIDOR", destinatario, conteudo);
        if (destinatario != null){
            findClientConnection(destinatario).ifPresent(conn -> {
                try{
                    conn.sendMessageToClient(systemMessage);
                } catch (IOException e){
                    System.err.println("[SERVIDOR] Erro ao transmitir para " + destinatario + ": " + e.getMessage());
                }
            });
        } else {
            for (ClientConnection clientConnection : clientList){
                try {
                    clientConnection.sendMessageToClient(systemMessage);
                } catch (IOException e) {
                    System.err.println("[SERVIDOR] Erro ao transmitir para " + clientConnection.userName + ": " + e.getMessage());
                }
            }
        }
    }

    // Encontra um ClientConnection pelo nome de usuário
    private static java.util.Optional<ClientConnection> findClientConnection(String userName){
        return clientList.stream()
                .filter(cc -> cc.userName.equalsIgnoreCase(userName))
                .findFirst();
    }


    @Override
    public void run() {
        try {
            Message messageFromClient;
            while (socket.isConnected()) {
                messageFromClient = (Message) objIn.readObject(); // lê objetos Message

                if (messageFromClient != null) {
                    System.out.println("[SERVIDOR] Recebido de " + messageFromClient.getSender() + ": " + messageFromClient.getContent() + " para " + messageFromClient.getReceiver());
                    // Requisito: Quando destinatario == null, broadcast
                    if (messageFromClient.getReceiver() == null) {
                        // Comando de lista de usuários
                        if (messageFromClient.getContent().trim().equalsIgnoreCase("/usuarios")) {
                            sendUserList(messageFromClient.getSender());
                        } else {
                            // Broadcast normal
                            System.out.println("[SERVIDOR] Transmitindo mensagem de " + messageFromClient.getSender());
                            broadcastMessage(messageFromClient);
                        }
                    } else {
                        // Requisito: Mensagens privadas
                        // "Quando uma mensagem for privada o seu campo conteúdo deve seguir o formato /privado:mensagem.
                        // Ao receber essa mensagem o servidor deve remover o prefixo /privado: do conteúdo,
                        // e encaminhá-la para o cliente especificado no destinatário."

                        ClientConnection targetClient = findClientConnection(messageFromClient.getReceiver()).orElse(null);

                        if (targetClient != null) {
                            String content = messageFromClient.getContent();
                            if (content.toLowerCase().startsWith("/privado:")) {
                                String privateContent = content.substring("/privado:".length()).trim();
                                Message privateMessageToSend = new Message(
                                        messageFromClient.getSender(),
                                        messageFromClient.getReceiver(),
                                        privateContent
                                );
                                System.out.println("[SERVIDOR] Enviando mensagem privada de " + privateMessageToSend.getSender() + " para " + privateMessageToSend.getReceiver());
                                targetClient.sendMessageToClient(privateMessageToSend);
                            } else {
                                // Outro tipo de mensagem direta (não especificado como privada com /privado:)
                                // Pode ser um comando direcionado ou mensagem normal que o cliente setou destinatário
                                System.out.println("[SERVER] Enviando mensagem direta de " + messageFromClient.getSender() + " para " + messageFromClient.getReceiver());
                                targetClient.sendMessageToClient(messageFromClient);
                            }
                        } else {
                            // Usuário destinatário não encontrado
                            System.out.println("[SERVIDOR] Destinatário da mensagem privada '" + messageFromClient.getReceiver() + "' não encontrado.");
                            Message errorMsg = new Message("SERVIDOR", messageFromClient.getSender(), "Usuário '" + messageFromClient.getReceiver() + "' não encontrado ou não conectado.");
                            sendMessageToClient(errorMsg);
                        }
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
        System.err.println("[SERVIDOR] Cliente " + (userName != null ? userName : "desconhecido") + " desconectado");
        } finally {
            closeConnection();
        }
    }

    private void sendUserList(String solicitanteUsername)  {
        StringBuilder userListString = new StringBuilder("Usuários conectados: ");
        for (ClientConnection cc : clientList) {
            userListString.append(cc.userName).append(", ");
        }
        // Remove a última vírgula e espaço
        if (userListString.length() > "Usuários conectados: ".length()) {
            userListString.setLength(userListString.length() - 2);
        } else {
            userListString.append("(Ninguem além de você)");
        }

        Message userListMessage = new Message("SERVIDOR", solicitanteUsername, userListString.toString());
        try {
            sendMessageToClient(userListMessage);
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro ao enviar lista de usuários para " + solicitanteUsername + ": " + e.getMessage());
        }
    }


    public void removeClient() {
        clientList.remove(this);
        if (userName != null) {
            System.out.println("[SERVIDOR] " + userName + " está desconectado. Clientes restantes: " + clientList.size());
            broadcastSystemMessage(userName + " deixou o chat.", null);
        }
    }

    private void closeConnection() {
        removeClient();
        try {
            if (objIn != null) objIn.close();
            if (objOut != null) objOut.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro ao fechar recursos para " + (userName != null ? userName : "socket") + ": " + e.getMessage());
        }
    }
}
