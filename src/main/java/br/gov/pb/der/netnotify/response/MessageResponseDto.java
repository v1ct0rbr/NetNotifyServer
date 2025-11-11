package br.gov.pb.der.netnotify.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.gov.pb.der.netnotify.model.Message;
import lombok.Data;

@Data
public class MessageResponseDto implements Serializable {

    private UUID id;
    private String title;
    private String content;
    private String level;
    private String messageType;
    private String user;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
        
    private Boolean sendToSubdivisions;
    private Integer repeatIntervalMinutes;
 
    private LocalDateTime expireAt;
    private LocalDateTime lastSentAt;
    private List<DepartmentInfo> departments;
    
    public MessageResponseDto() {
    }

    public MessageResponseDto(Message message) {
        this.title = message.getTitle();
        this.content = message.getContent();
        this.level = message.getLevel().getName();
        this.messageType = message.getType().getName();
        this.user = message.getUser().getUsername();
        this.createdAt = message.getCreatedAt();
        this.updatedAt = message.getUpdatedAt();
        if (message.getDepartments() != null) {
            this.departments = message.getDepartments().stream()
                .map(d -> new DepartmentInfo(d.getId(), d.getName()))
                .toList();
        }
    }

    public MessageResponseDto(UUID id, String title, String content, String level, String messageType, String user,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expireAt, LocalDateTime lastSentAt,
             Integer repeatIntervalMinutes, Boolean sendToSubdivisions) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.level = level;
        this.messageType = messageType;
        this.createdAt = createdAt;
        this.user = user;
        this.updatedAt = updatedAt;
        this.expireAt = expireAt;
        this.sendToSubdivisions = sendToSubdivisions;
        this.repeatIntervalMinutes = repeatIntervalMinutes;
        this.lastSentAt = lastSentAt;        
        
    }

    
    public String jsonStringfy() {
        return "{" +
            "\"id\":" + "\"" + id + "\"" +
            ", \"title\":" + "\"" + title + "\"" +
            ", \"content\":" + "\"" + content + "\"" +
            ", \"level\":" + "\"" + level + "\"" +
            ", \"type\":" + "\"" + messageType + "\"" +
            ", \"user\":" + "\"" + user + "\"" +
            ", \"createdAt\":" + "\"" + createdAt + "\"" +
            ", \"updatedAt\":" + "\"" + updatedAt + "\"" +
            (departments != null ? ", \"departments\":" + departments.toString() : "") +
            "}";
    }
    @lombok.AllArgsConstructor
    @lombok.Data
    public static class DepartmentInfo implements java.io.Serializable {
        private java.util.UUID id;
        private String name;
    }
}
