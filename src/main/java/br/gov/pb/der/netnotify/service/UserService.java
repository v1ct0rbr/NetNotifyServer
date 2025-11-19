package br.gov.pb.der.netnotify.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.repository.UserRepository;
import br.gov.pb.der.netnotify.security.KeycloakUser;
import br.gov.pb.der.netnotify.security.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que gerencia usuários locais integrados com Keycloak Este serviço
 * sincroniza dados entre Keycloak e banco local
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Profile("!dev") // Exclui do profile dev
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakUserService keycloakUserService;

    /**
     * Obtém ou cria um usuário local baseado no usuário do Keycloak
     */
    public User getOrCreateUser() {
        KeycloakUser keycloakUser = keycloakUserService.getCurrentUser();

        if (keycloakUser == null) {
            throw new IllegalStateException("Usuário não está autenticado no Keycloak");
        }

        return getOrCreateUser(keycloakUser);
    }

    /**
     * Obtém ou cria um usuário local baseado nos dados do Keycloak
     */
    public User getOrCreateUser(KeycloakUser keycloakUser) {
        UUID userId = UUID.fromString(keycloakUser.getId());
        Optional<User> existingUser = userRepository.findById(userId);

        if (existingUser.isPresent()) {
            // Atualiza dados do usuário existente
            User user = existingUser.get();
            syncUserWithKeycloak(user, keycloakUser);
            user.registerLogin();
            return userRepository.save(user);
        } else {
            // Cria novo usuário
            return createUserFromKeycloak(keycloakUser);
        }
    }

    public User getLoggedUser() {
        KeycloakUser keycloakUser = keycloakUserService.getCurrentUser();

        if (keycloakUser == null) {
            throw new IllegalStateException("Usuário não está autenticado no Keycloak");
        }

        UUID userId = UUID.fromString(keycloakUser.getId());
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário local não encontrado"));
    }

    /**
     * Cria um novo usuário local baseado nos dados do Keycloak
     */
    private User createUserFromKeycloak(KeycloakUser keycloakUser) {
        log.info("Criando novo usuário local para: {}", keycloakUser.getUsername());

        UUID userId = UUID.fromString(keycloakUser.getId());
        User user = User.builder()
                .id(userId)
                .username(keycloakUser.getUsername())
                .email(keycloakUser.getEmail())
                .fullName(keycloakUser.getFullName())
                .firstLogin(LocalDateTime.now())
                .lastLogin(LocalDateTime.now())
                .active(true)
                .theme(User.Theme.AUTO)
                .language("pt-BR")
                .timezone("America/Sao_Paulo")
                .build();

        // Mapear roles do Keycloak para roles da aplicação

        user.setRoles(keycloakUser.getRoles());
        return userRepository.save(user);
    }

    /**
     * Sincroniza dados do usuário local com o Keycloak
     */
    private void syncUserWithKeycloak(User user, KeycloakUser keycloakUser) {
        boolean changed = false;

        if (!keycloakUser.getUsername().equals(user.getUsername())
                || !keycloakUser.getEmail().equals(user.getEmail())
                || !keycloakUser.getFullName().equals(user.getFullName())) {

            user.syncWithKeycloak(
                    keycloakUser.getUsername(),
                    keycloakUser.getEmail(),
                    keycloakUser.getFullName());
            changed = true;
        }

        user.setRoles(keycloakUser.getRoles());
        // Atualizar roles se necessário

        if (changed) {
            log.debug("Sincronizando dados do usuário: {}", user.getUsername());
        }
    }

    /**
     * Busca usuário por ID do Keycloak
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID keycloakId) {
        return userRepository.findById(keycloakId);
    }

    /**
     * Busca usuário por username
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Lista todos os usuários ativos
     */
    @Transactional(readOnly = true)
    public List<User> findActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    /**
     * Atualiza preferências do usuário
     */
    public User updateUserPreferences(UUID keycloakId, String preferences,
            User.Theme theme, String language, String timezone) {
        User user = userRepository.findById(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setPreferences(preferences);
        user.setTheme(theme);
        user.setLanguage(language);
        user.setTimezone(timezone);

        return userRepository.save(user);
    }

    /**
     * Desativa usuário
     */
    public void deactivateUser(UUID keycloakId) {
        User user = userRepository.findById(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setActive(false);
        userRepository.save(user);

        log.info("Usuário desativado: {}", user.getUsername());
    }

    /**
     * Reativa usuário
     */
    public void activateUser(UUID keycloakId) {
        User user = userRepository.findById(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setActive(true);
        userRepository.save(user);

        log.info("Usuário reativado: {}", user.getUsername());
    }

    /**
     * Estatísticas de usuários
     */
    @Transactional(readOnly = true)
    public UserStats getUserStats() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return UserStats.builder()
                .totalActiveUsers(userRepository.countByActiveTrue())
                .newUsersToday(userRepository.countNewUsersToday(startOfDay, endOfDay))
                .activeUsersLast24h(userRepository.countActiveUsersSince(LocalDateTime.now().minusDays(1)))
                .build();
    }

    /**
     * Classe para estatísticas de usuários
     */
    public record UserStats(
            long totalActiveUsers,
            long newUsersToday,
            long activeUsersLast24h) {

        public static UserStatsBuilder builder() {
            return new UserStatsBuilder();
        }

        public static class UserStatsBuilder {

            private long totalActiveUsers;
            private long newUsersToday;
            private long activeUsersLast24h;

            public UserStatsBuilder totalActiveUsers(long totalActiveUsers) {
                this.totalActiveUsers = totalActiveUsers;
                return this;
            }

            public UserStatsBuilder newUsersToday(long newUsersToday) {
                this.newUsersToday = newUsersToday;
                return this;
            }

            public UserStatsBuilder activeUsersLast24h(long activeUsersLast24h) {
                this.activeUsersLast24h = activeUsersLast24h;
                return this;
            }

            public UserStats build() {
                return new UserStats(totalActiveUsers, newUsersToday, activeUsersLast24h);
            }
        }
    }
}
