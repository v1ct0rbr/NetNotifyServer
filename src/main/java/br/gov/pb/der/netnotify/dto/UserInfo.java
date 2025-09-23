package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;
import java.util.List;

import br.gov.pb.der.netnotify.model.Role;
import br.gov.pb.der.netnotify.model.User;
import lombok.Data;

@Data
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    // fullName?: string;
    // username: string;
    // email?: string;
    // roles: Role[];

    private String fullName;
    private String username;
    private String email;
    private List<Role> roles;

    public UserInfo() {
    }

    public UserInfo(User userDetails) {
        this.fullName = userDetails.getFullName();
        this.username = userDetails.getUsername();
        this.email = userDetails.getEmail();
        this.roles = userDetails.getRoles();
    }

    // Getters and Setters
}
