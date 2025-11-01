package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;
import java.util.Set;

import br.gov.pb.der.netnotify.model.User;
import lombok.Data;

@Data
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fullName;
    private String username;
    private String email;
    private Set<String> roles;

    public UserInfo() {
    }

    public UserInfo(User userDetails) {
        this.fullName = userDetails.getFullName();
        this.username = userDetails.getUsername();
        this.email = userDetails.getEmail();
        this.roles = userDetails.getRoles();
    }

    public UserInfo(br.gov.pb.der.netnotify.security.KeycloakUser keycloakUser) {
        this.fullName = keycloakUser.getFullName();
        this.username = keycloakUser.getUsername();
        this.email = keycloakUser.getEmail();
        // Roles can be set if needed
        this.roles = keycloakUser.getRoles();
    }

    // Getters and Setters
}
