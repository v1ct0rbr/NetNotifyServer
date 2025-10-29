package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;
import java.util.Set;

import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.model.User.ApplicationRole;
import lombok.Data;

@Data
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fullName;
    private String username;
    private String email;
    private Set<ApplicationRole> roles;

    public UserInfo() {
    }

    public UserInfo(User userDetails) {
        this.fullName = userDetails.getFullName();
        this.username = userDetails.getUsername();
        this.email = userDetails.getEmail();
        this.roles = userDetails.getApplicationRoles();
    }

    // Getters and Setters
}
