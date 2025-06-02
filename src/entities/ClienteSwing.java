package entities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.format.DateTimeFormatter;

public class ClienteSwing extends JFrame {
    private JTextArea areaTextoChat;
    private JTextField campoEntradaMensagem;
    private JButton botaoEnviar;

    private Socket socket;
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;
    private String username;
    private String serverAddress = "127.0.0.1";
    private int serverPort = 8501;

    public ClienteSwing() {
        while (true) {
            this.username = JOptionPane.showInputDialog(this, "Digite seu nome de usuário:", "Login", JOptionPane.PLAIN_MESSAGE);
            if (this.username == null) {
                // Se o usuário cancelar (pressionar "Cancelar" ou fechar a janela), confirmamos se quer sair.
                int confirm = JOptionPane.showConfirmDialog(this, "Deseja realmente sair?", "Confirmar Saída", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
                // Caso contrário, continua no loop para digitar novamente.
                continue;
            }
            boolean usuarioValido = verificaUsuario();
            if (!usuarioValido){
                JOptionPane.showMessageDialog(this, "Usuário inválido! Não são permitidos espaços ou usuários vazios.", "Erro ao escolher usuário", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            try {
                fecharConexao();
                conectarAoServidor();
            } catch (UnknownHostException e) {
                JOptionPane.showMessageDialog(this, "Servidor não encontrado: " + e.getMessage(), "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Erro de E/S ao conectar: " + e.getMessage(), "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            try {
                boolean usuarioEmUso = objIn.readBoolean();
                System.out.println("Usuario em uso: " + usuarioEmUso);
                if (usuarioEmUso){
//                    fecharConexao();
                    JOptionPane.showMessageDialog(this, "O usuário escolhido já está em uso! ", "Usuário em uso", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            break;
        }


        configurarUI();
        inicializarInterfaceChat();
        iniciarEscutaDeMensagens();
    }

    private boolean verificaUsuario(){
        if (this.username.trim().isEmpty()) {
            return false;
        }
        if (this.username.contains(" ")){
            return false;
        }
        return true;
    }


    private void configurarUI() {
        setTitle("Chat TCP - Cliente: " + username);
        setSize(500, 500);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        areaTextoChat = new JTextArea();
        areaTextoChat.setEditable(false);
        areaTextoChat.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(areaTextoChat), BorderLayout.CENTER);

        JPanel painelInferior = new JPanel(new BorderLayout());
        campoEntradaMensagem = new JTextField();
        botaoEnviar = new JButton("Enviar");

        painelInferior.add(campoEntradaMensagem, BorderLayout.CENTER);
        painelInferior.add(botaoEnviar, BorderLayout.EAST);
        add(painelInferior, BorderLayout.SOUTH);

        ActionListener enviarAcao = e -> enviarMensagemDoCampo();

        campoEntradaMensagem.addActionListener(enviarAcao);
        botaoEnviar.addActionListener(enviarAcao);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmarSaida();
            }
        });
    }

    private void conectarAoServidor() throws IOException {
        socket = new Socket(serverAddress, serverPort);
        objOut = new ObjectOutputStream(socket.getOutputStream());
        objIn = new ObjectInputStream(socket.getInputStream());
        objOut.writeObject(username);
        objOut.flush();
    }

    private void inicializarInterfaceChat() {
        areaTextoChat.append("Conectado ao servidor como " + username + "\n");
        areaTextoChat.append("Digite '/privado <usuario> <mensagem>' para msg privada.\n");
        areaTextoChat.append("Digite '/usuarios' para listar usuários.\n");
        areaTextoChat.append("Digite '/quit' ou feche a janela para sair.\n");
    }

    private void enviarMensagemDoCampo() {
        String texto = campoEntradaMensagem.getText().trim();
        if (!texto.isEmpty()) {
            Message mensagemParaEnviar;

            if (texto.equalsIgnoreCase("/quit")) {
                confirmarSaida();
                return;
            } else if (texto.equalsIgnoreCase("/usuarios")) {
                mensagemParaEnviar = new Message(username, null, "/usuarios");
            } else if (texto.toLowerCase().startsWith("/privado ")) {
                String[] partes = texto.split(" ", 3);
                if (partes.length < 3) {
                    areaTextoChat.append("[AVISO] Formato inválido. Use: /privado <usuario> <mensagem>\n");
                    campoEntradaMensagem.setText("");
                    return;
                }
                String destinatario = partes[1];
                String conteudoPrivado = partes[2];
                mensagemParaEnviar = new Message(username, destinatario, "/privado:" + conteudoPrivado);
                // Exibe a mensagem privada enviada na própria tela
                areaTextoChat.append(String.format("[%s] Você (privado para %s): %s\n",
                        java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        destinatario,
                        conteudoPrivado));

            } else {
                mensagemParaEnviar = new Message(username, null, texto);
                areaTextoChat.append(String.format("[%s] Você: %s\n",
                        java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        texto));
            }

            try {
                objOut.writeObject(mensagemParaEnviar);
                objOut.flush();
            } catch (IOException ex) {
                areaTextoChat.append("[ERRO] Falha ao enviar mensagem: " + ex.getMessage() + "\n");
            }
            campoEntradaMensagem.setText("");
        }
    }

    private void iniciarEscutaDeMensagens() {
        new Thread(() -> {
            try {
                while (socket.isConnected() && !socket.isClosed()) {
                    Message mensagemRecebida = (Message) objIn.readObject();
                    if (mensagemRecebida != null) {
                        final String mensagemFormatada = formatarMensagem(mensagemRecebida);
                        // Atualiza a UI na Event Dispatch Thread (EDT)
                        SwingUtilities.invokeLater(() -> {
                            areaTextoChat.append(mensagemFormatada + "\n");
                            // Auto-scroll para o final
                            areaTextoChat.setCaretPosition(areaTextoChat.getDocument().getLength());
                        });
                    }
                }
            } catch (EOFException e) {
                SwingUtilities.invokeLater(() -> areaTextoChat.append("[INFO] Conexão com o servidor perdida (EOF).\n"));
            }
            catch (IOException | ClassNotFoundException e) {
                if (!socket.isClosed()) {
                    SwingUtilities.invokeLater(() -> areaTextoChat.append("[ERRO] Erro ao escutar mensagens: " + e.getMessage() + "\n"));
                }
            } finally {
                fecharConexao();
            }
        }).start();
    }

    private String formatarMensagem(Message msg) {
        String horaFormatada = msg.getSentTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String remetente = msg.getSender();
        String conteudo = msg.getContent();

        if ("SERVER".equalsIgnoreCase(remetente)) {
            return String.format("[%s] [SERVIDOR]: %s", horaFormatada, conteudo);
        } else if (msg.getReceiver() != null && msg.getReceiver().equalsIgnoreCase(this.username)) {
            // Mensagem privada para mim
            return String.format("[%s] %s (privado): %s", horaFormatada, remetente, conteudo);
        } else {
            // Mensagem de broadcast (não enviada por mim, pois essas já são tratadas ao enviar)
            if (!remetente.equalsIgnoreCase(this.username)) {
                return String.format("[%s] %s: %s", horaFormatada, remetente, conteudo);
            }
            return null; // Não re-exibir minhas próprias mensagens de broadcast aqui
        }
    }

    private void confirmarSaida() {
        int resposta = JOptionPane.showConfirmDialog(this, "Deseja realmente sair do chat?", "Confirmar Saída", JOptionPane.YES_NO_OPTION);
        if (resposta == JOptionPane.YES_OPTION) {
            fecharConexao();
            dispose();
            System.exit(0);
        }
    }

    private void fecharConexao() {
        try {
            if (areaTextoChat != null)
                areaTextoChat.append("[INFO] Desconectando...\n");
            if (objOut != null) {
                objOut.close();
            }
            if (objIn != null) {
                objIn.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro menor ao fechar conexão: " + e.getMessage());
        } finally {
            if (campoEntradaMensagem != null)
                campoEntradaMensagem.setEnabled(false);
            if (botaoEnviar != null)
                botaoEnviar.setEnabled(false);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteSwing clienteGUI = new ClienteSwing();
            clienteGUI.setVisible(true);
        });
    }
}