package br.gov.pb.der.netnotify.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageDto {
    private UUID id;
    private String title;
    private String content;
    private String level;
    private String type;
    private String user;
    private LocalDateTime publishedAt;
    private LocalDateTime expireAt;
    private LocalDateTime lastSentAt;
    private List<DepartmentInfo> departments;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DepartmentInfo {
        private UUID id;
        private String name;
    }
}
