package br.gov.pb.der.netnotify.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import br.gov.pb.der.netnotify.model.Message;
import lombok.Data;

@Data
public class MessageResponseDto implements Serializable {

    private UUID id;
    private String content;
    private String level;
    private String messageType;
    private String user;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MessageResponseDto() {
    }

    public MessageResponseDto(Message message) {
        this.content = message.getContent();
        this.level = message.getLevel().getName();
        this.messageType = message.getType().getName();
        this.user = message.getUser().getUsername();
        this.createdAt = message.getCreatedAt();
        this.updatedAt = message.getUpdatedAt();
    }

    public MessageResponseDto(UUID id, String content, String level, String messageType, String user, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.content = content;
        this.level = level;
        this.messageType = messageType;
        this.createdAt = createdAt;
        this.user = user;
        this.updatedAt = updatedAt;
    }
}
