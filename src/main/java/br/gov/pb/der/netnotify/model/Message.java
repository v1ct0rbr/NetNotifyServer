package br.gov.pb.der.netnotify.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.gov.pb.der.netnotify.response.MessageResponseDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "level_id")
    private Level level;

    @ManyToOne
    @JoinColumn(name = "type_id")
    private MessageType type;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "repeat_interval_minutes")
    private Integer repeatIntervalMinutes;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToMany
    @JoinTable(name = "message_department", joinColumns = @JoinColumn(name = "message_id"), inverseJoinColumns = @JoinColumn(name = "department_id"))
    private List<Department> departments;

    @Column(name = "send_to_subdivisions")
    private Boolean sendToSubdivisions;

    public MessageResponseDto objectMapper() {
        return new MessageResponseDto(
                this.id,
                this.title,
                this.content,
                this.level != null ? this.level.getName() : null,
                this.type != null ? this.type.getName() : null,
                this.user != null ? this.user.getUsername() : null,
                this.createdAt,
                this.updatedAt,
                this.expireAt,
                this.lastSentAt,
                this.repeatIntervalMinutes,
                this.sendToSubdivisions,
                this.publishedAt);

    }

}
