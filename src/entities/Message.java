package entities;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private final String remetente;
    private final String destinatario;
    private final String conteudo;
    private final LocalDateTime horario;

    public Message(String remetente, String destinatario, String conteudo) {
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.conteudo = conteudo;
        this.horario = LocalDateTime.now();
    }

    public String getSender() {
        return remetente;
    }

    public String getReceiver() {
        return destinatario;
    }

    public String getContent() {
        return conteudo;
    }

    public LocalDateTime getSentTime() {
        return horario;
    }

    @Override
    public String toString() {
        return String.format("[%s] De: %s, Para: %s, Conteúdo: '%s'",
                horario.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME),
                remetente,
                (destinatario == null ? "TODOS" : destinatario),
                conteudo);
    }
}