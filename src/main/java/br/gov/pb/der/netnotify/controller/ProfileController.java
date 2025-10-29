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
        User user = userService.getLoggedUser();
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(user.getUsername());
        userInfo.setFullName(user.getFullName());
        userInfo.setEmail(user.getEmail());
        userInfo.setRoles(user.getApplicationRoles());
        return userInfo;
    }

}
