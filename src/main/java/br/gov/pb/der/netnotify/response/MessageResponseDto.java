package br.gov.pb.der.netnotify.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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

    private String scheduleDaysOfWeek;

    private String scheduleTimes;

    private String scheduleMonthDays;

    private String availabilityWindows;

    private LocalDateTime publishedAt;
    private LocalDateTime expireAt;
    private LocalDateTime lastSentAt;
    private Boolean paused;
    private List<DepartmentInfo> departments;
    public MessageResponseDto() {
        this.departments = new ArrayList<>();
    }

    public MessageResponseDto(Message message) {
        this.title = message.getTitle();
        this.content = message.getContent();
        this.level = message.getLevel().getName();
        this.messageType = message.getType().getName();
        this.user = message.getUser().getUsername();
        this.createdAt = message.getCreatedAt();
        this.updatedAt = message.getUpdatedAt();
        this.scheduleDaysOfWeek = message.getScheduleDaysOfWeek();
        this.scheduleTimes = message.getScheduleTimes();
        this.scheduleMonthDays = message.getScheduleMonthDays();
        this.availabilityWindows = message.getAvailabilityWindows();
        this.paused = Boolean.TRUE.equals(message.getPaused());
        if (message.getDepartments() != null) {
            this.departments = message.getDepartments().stream()
                    .map(d -> new DepartmentInfo(d.getId(), d.getName()))
                    .toList();
        }
    }

    public MessageResponseDto(UUID id, String title, String content, String level, String messageType, String user,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expireAt, LocalDateTime lastSentAt,
            Integer repeatIntervalMinutes, Boolean sendToSubdivisions, LocalDateTime publishedAt,
            String scheduleDaysOfWeek, String scheduleTimes, String scheduleMonthDays, String availabilityWindows,
            Boolean paused) {
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
        this.publishedAt = publishedAt;
        this.scheduleDaysOfWeek = scheduleDaysOfWeek;
        this.scheduleTimes = scheduleTimes;
        this.scheduleMonthDays = scheduleMonthDays;
        this.availabilityWindows = availabilityWindows;
        this.paused = paused;
    }

    public String jsonStringfy() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("level", level);
        payload.put("type", messageType);
        payload.put("user", user);
        payload.put("createdAt", createdAt);
        payload.put("updatedAt", updatedAt);
        if (departments != null && !departments.isEmpty()) {
            payload.put("departments", departments);
        }
        try {
            return new ObjectMapper().registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Data
    public static class DepartmentInfo implements java.io.Serializable {

        private java.util.UUID id;
        private String name;
    }
}
