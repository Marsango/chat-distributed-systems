package entities;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class ClientConnection extends Thread {
    private Socket socket;
    private static final List<ClientConnection> clientList = new CopyOnWriteArrayList<>();
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;
    private String userName;
    private final List<String> ignoredList = new ArrayList<>();

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
            if (clientConnection.userName.equalsIgnoreCase(this.userName)){
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
            if(!clientConnection.userName.equals(messageToBroadcast.getSender()) && clientConnection.findUserIgnored(messageToBroadcast.getSender()).isEmpty()){
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

    private java.util.Optional<String> findUserIgnored(String userToFind){
        return ignoredList.stream().filter(user -> user.equalsIgnoreCase(userToFind)).findFirst();
    }

    public void handleGlobalMessage(Message messageFromClient){
        // Comando de lista de usuários
        if (messageFromClient.getContent().trim().equalsIgnoreCase("/usuarios")) {
            sendUserList(messageFromClient.getSender());
        } else if (messageFromClient.getContent().trim().startsWith("/ignorados")) {
            sendIgnoredList(messageFromClient.getSender());
        } else {
            // Broadcast normal
            System.out.println("[SERVIDOR] Transmitindo mensagem de " + messageFromClient.getSender());
            broadcastMessage(messageFromClient);
        }
    }

    public void handlePrivateMessages(Message messageFromClient, ClientConnection targetClient) throws IOException {
        String content = messageFromClient.getContent();
        String privateContent = content.substring("/privado:".length()).trim();
        Message privateMessageToSend = new Message(
                messageFromClient.getSender(),
                messageFromClient.getReceiver(),
                privateContent
        );
        if (targetClient.findUserIgnored(messageFromClient.getSender()).isEmpty()){
            System.out.println("[SERVIDOR] Enviando mensagem privada de " + privateMessageToSend.getSender() + " para " + privateMessageToSend.getReceiver());
            targetClient.sendMessageToClient(privateMessageToSend);
            return;
        }
        System.out.println("[SERVIDOR] Mensagem privada de " + privateMessageToSend.getSender() + " para " + privateMessageToSend.getReceiver() + " não enviada pois o destinatário ignorou o remetente.");
        sendMessageToClient(new Message("SERVIDOR", messageFromClient.getSender(), "Sua mensagem não foi enviada pois o usuário ignorou você."));
    }

    public void handleIgnore(Message message) throws IOException {
        if (findUserIgnored(message.getReceiver()).isPresent()){
            System.out.println("[SERVIDOR] O usuário '" + message.getReceiver() + "' já está ignorado pelo usuário " + message.getSender());
            Message errorMsg = new Message("SERVIDOR", userName, "Você já ignorou este usuário.");
            sendMessageToClient(errorMsg);
            return;
        }
        ignoredList.add(message.getReceiver());
        System.out.println("[SERVIDOR] O usuário '" + message.getReceiver() + "' foi ignorado pelo usuário " + message.getSender());
        Message returnMessage = new Message("SERVIDOR", userName, "Usuário ignorado com sucesso.");
        sendMessageToClient(returnMessage);
    }

    public void handleUnignore(Message message) throws IOException {
        if (findUserIgnored(message.getReceiver()).isEmpty()){
            System.out.println("[SERVIDOR] O usuário '" + message.getReceiver() + "' não foi encontrado na lista de ignorados pelo usuário " + message.getSender());
            Message errorMsg = new Message("SERVIDOR", userName, "O usuário não foi encontrado na lista de ignorados.");
            sendMessageToClient(errorMsg);
            return;
        }
        ignoredList.remove(message.getReceiver());
        System.out.println("[SERVIDOR] O usuário '" + message.getReceiver() + "' foi designorado pelo usuário " + message.getSender());
        Message returnMessage = new Message("SERVIDOR", userName, "Usuário designorado com sucesso.");
        sendMessageToClient(returnMessage);
    }

    public void handleMessageWithTarget(Message messageFromClient) throws IOException {
        ClientConnection targetClient = findClientConnection(messageFromClient.getReceiver()).orElse(null);

        if (messageFromClient.getReceiver().equals(messageFromClient.getSender())){
            System.out.println("[SERVIDOR] Destinatário da ação '" + messageFromClient.getReceiver() + "' é o próprio usuário, ação não realizada.");
            Message errorMsg = new Message("SERVIDOR", messageFromClient.getSender(), "Usuário alvo '" + messageFromClient.getReceiver() + "' é o próprio usuário, ação não realizada.");
            sendMessageToClient(errorMsg);
            return;
        }

        if (targetClient != null) {
            if (messageFromClient.getContent().startsWith("/privado:")){
                handlePrivateMessages(messageFromClient, targetClient);
            } else if (messageFromClient.getContent().equalsIgnoreCase("/ignorar")) {
                handleIgnore(messageFromClient);
            } else if (messageFromClient.getContent().equalsIgnoreCase("/designorar")) {
                handleUnignore(messageFromClient);
            } else {
                //Em tese, nunca entra nesse else.
                System.out.println("[SERVIDOR] Ação inválida " + messageFromClient.getContent());
                Message errorMsg = new Message("SERVIDOR", messageFromClient.getSender(), "Ação inválida '" + messageFromClient.getReceiver());
                sendMessageToClient(errorMsg);
            }

        } else {
            // Usuário destinatário não encontrado
            System.out.println("[SERVIDOR] Destinatário da ação '" + messageFromClient.getReceiver() + "' não encontrado.");
            Message errorMsg = new Message("SERVIDOR", messageFromClient.getSender(), "Usuário '" + messageFromClient.getReceiver() + "' não encontrado ou não conectado.");
            sendMessageToClient(errorMsg);
        }
    }

    public void handleMessages(Message messageFromClient) throws IOException {
        if (messageFromClient != null) {
            System.out.println("[SERVIDOR] Recebido de " + messageFromClient.getSender() + ": " + messageFromClient.getContent() + " para " + messageFromClient.getReceiver());
            // Requisito: Quando destinatario == null, broadcast
            if (messageFromClient.getReceiver() == null) {
                handleGlobalMessage(messageFromClient);
            } else {
                // Requisito: Mensagens privadas
                // "Quando uma mensagem for privada o seu campo conteúdo deve seguir o formato /privado:mensagem.
                // Ao receber essa mensagem o servidor deve remover o prefixo /privado: do conteúdo,
                // e encaminhá-la para o cliente especificado no destinatário."
                handleMessageWithTarget(messageFromClient);
            }
        }
    }


    @Override
    public void run() {
        try {
            Message messageFromClient;
            while (socket.isConnected()) {
                messageFromClient = (Message) objIn.readObject(); // lê objetos Message
                handleMessages(messageFromClient);
            }
        } catch (IOException | ClassNotFoundException e) {
        System.err.println("[SERVIDOR] Cliente " + (userName != null ? userName : "desconhecido") + " desconectado");
        } finally {
            closeConnection();
        }
    }

    private void sendUserList(String requesterUsername)  {
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

        Message userListMessage = new Message("SERVIDOR", requesterUsername, userListString.toString());
        try {
            sendMessageToClient(userListMessage);
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro ao enviar lista de usuários para " + requesterUsername + ": " + e.getMessage());
        }
    }

    private void sendIgnoredList(String requesterUsername){
        Message userIgnoredMessage;
        if (ignoredList.isEmpty()){
            userIgnoredMessage = new Message("SERVIDOR", requesterUsername, "Não há usuários ignorados.");
        } else {
            StringBuilder userIgnoredString = new StringBuilder("Usuários ignorados: ");
            for (String user : ignoredList){
                userIgnoredString.append(user).append(", ");
            }
            userIgnoredString.setLength(userIgnoredString.length() - 2);
            userIgnoredMessage = new Message("SERVIDOR", requesterUsername, userIgnoredString.toString());
        }
        try {
            sendMessageToClient(userIgnoredMessage);
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro ao enviar lista de usuários ignorados para " + requesterUsername + ": " + e.getMessage());
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
