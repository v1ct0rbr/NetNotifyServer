package br.gov.pb.der.netnotify.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.gov.pb.der.netnotify.model.Department;
import br.gov.pb.der.netnotify.model.Message;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDto implements java.io.Serializable {

    private String id;
    @Size(min = 10, max = 100, message = "O título deve ter entre 10 e 100 caracteres")
    private String title;

    @Size(min = 10, max = 10000, message = "O conteúdo deve ter entre 10 e 10000 caracteres")
    private String content;

    @Min(value = 1, message = "O nível deve ser maior que 0")
    @Max(value = 5, message = "O nível deve ser menor que 6")
    private Integer level;

    @Max(value = 5, message = "O tipo de mensagem deve ser menor que 6")
    @Min(value = 1, message = "O tipo de mensagem deve ser maior que 0")
    private Integer type;

    @JsonProperty("sendToSubdivisions")
    private Boolean sendToSubdivisions;

    @JsonProperty("expireAt")
    @DateTimeFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime expireAt;

    @JsonProperty("repeatIntervalMinutes")
    private Integer repeatIntervalMinutes;

    @JsonProperty("publishedAt")
    @DateTimeFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime publishedAt;

    private List<UUID> departments;

    public MessageDto() {
    }

    public MessageDto(String title, String content, Integer level, Integer type, List<UUID> departments,
            Boolean sendToSubdivisions,
            LocalDateTime expireAt, LocalDateTime publishedAt, Integer repeatIntervalMinutes) {
        this.sendToSubdivisions = sendToSubdivisions;
        this.expireAt = expireAt;
        this.publishedAt = publishedAt;
        this.title = title;
        this.content = content;
        this.level = level;
        this.type = type;
        this.departments = departments;
        this.repeatIntervalMinutes = repeatIntervalMinutes;
    }

    public static MessageDto fromMessage(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId().toString());
        dto.setTitle(message.getTitle());
        dto.setContent(message.getContent());
        dto.setLevel(message.getLevel() != null ? message.getLevel().getId() : null);
        dto.setType(message.getType() != null ? message.getType().getId() : null);
        dto.setSendToSubdivisions(message.getSendToSubdivisions());
        dto.setExpireAt(message.getExpireAt());
        dto.setPublishedAt(message.getPublishedAt());
        if (message.getDepartments() != null) {
            dto.setDepartments(
                    message.getDepartments().stream().map(Department::getId).toList());
        }
        return dto;
    }

}
