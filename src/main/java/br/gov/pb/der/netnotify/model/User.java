package br.gov.pb.der.netnotify.model;

import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade User que armazena informações complementares aos dados do Keycloak
 * Esta entidade NÃO substitui o Keycloak, mas complementa com dados específicos
 * da aplicação
 */
@Entity
@Table(name = "users", schema = "auth", indexes = {
        @Index(name = "idx_user_keycloak_id", columnList = "keycloak_id"),
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_username", columnList = "username")
})

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * ID do usuário no Keycloak (Subject do JWT)
     * Este é o campo que faz a ligação com o Keycloak
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id; // ID vem diretamente do Keycloak, não é gerado automaticamente

    /**
     * Nome de usuário (sincronizado com Keycloak)
     */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /**
     * Email do usuário (sincronizado com Keycloak)
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Nome completo (sincronizado com Keycloak)
     */
    @Column(name = "full_name", length = 255)
    private String fullName;

    /**
     * Primeira vez que o usuário acessou a aplicação
     */
    @Column(name = "first_login")
    private LocalDateTime firstLogin;

    /**
     * Último acesso à aplicação
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Se o usuário está ativo na aplicação (independente do Keycloak)
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Preferências do usuário (JSON)
     */
    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;

    /**
     * Tema preferido da interface
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme")
    @Builder.Default
    private Theme theme = Theme.AUTO;

    /**
     * Idioma preferido
     */
    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "pt-BR";

    /**
     * Timezone do usuário
     */
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    /**
     * Roles locais específicas da aplicação (complementam as do Keycloak)
     */
    /*
     * @ElementCollection(fetch = FetchType.EAGER)
     * 
     * @CollectionTable(
     * name = "user_roles",
     * joinColumns = @JoinColumn(name = "user_id")
     * )
     * 
     * @Column(name = "role")
     * 
     * @Enumerated(EnumType.STRING)
     */
    @Transient
    private Set<String> roles;

    /**
     * Data de criação do registro
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Data da última atualização
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Enum para temas da interface
     */
    public enum Theme {
        LIGHT, DARK, AUTO
    }

    /**
     * Verifica se o usuário tem uma role específica da aplicação
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains("ROLE_" + role);
    }

    /**
     * Registra um novo login
     */
    public void registerLogin() {
        if (this.firstLogin == null) {
            this.firstLogin = LocalDateTime.now();
        }
        this.lastLogin = LocalDateTime.now();
    }

    /**
     * Sincroniza dados básicos com o Keycloak
     */
    public void syncWithKeycloak(String username, String email, String fullName) {
        this.username = username;
        this.email = email;
        this.fullName = fullName;
    }

}