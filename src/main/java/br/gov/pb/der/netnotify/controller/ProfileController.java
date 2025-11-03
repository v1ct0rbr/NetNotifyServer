package br.gov.pb.der.netnotify.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.UserInfo;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/me")
    public UserInfo getProfile() {
        System.out.println("Fetching profile for logged-in user");
        // Garante que o usuário local exista; cria/sincroniza a partir do JWT/Keycloak se necessário
        User user = userService.getOrCreateUser();
        System.out.println("Logged-in user ID: " + user.getId());
        for (String role : user.getRoles()) {
            System.out.println("User role: " + role);
        }
        UserInfo userInfo = new UserInfo(user);
        return userInfo;
    }

}
