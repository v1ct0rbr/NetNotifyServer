package br.gov.pb.der.netnotify.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.auth.UserDetailsImpl;
import br.gov.pb.der.netnotify.dto.LoginUserDto;
import br.gov.pb.der.netnotify.dto.RecoveryJwtTokenDto;
import br.gov.pb.der.netnotify.model.Role;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.service.JwtService;
import br.gov.pb.der.netnotify.service.LdapService;
import br.gov.pb.der.netnotify.service.UserService;
import lombok.RequiredArgsConstructor;

@RequestMapping("/auth/")
@RestController
@RequiredArgsConstructor
public class AuthenticationController {

    private final UserService userService;

    private final LdapService ldapService;

    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<RecoveryJwtTokenDto> authenticateUser(@RequestBody LoginUserDto loginUserDto) {
        RecoveryJwtTokenDto token = userService.authenticateUser(loginUserDto);

        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PostMapping("/ldap-login")
    public ResponseEntity<RecoveryJwtTokenDto> authenticateLdapUser(@RequestBody LoginUserDto loginUserDto) {
        LdapService.AuthResult authResult = ldapService.authenticateWithGroups(loginUserDto.getUsername(),
                loginUserDto.getPassword());
        if (!authResult.authenticated) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        // Define role conforme admin ou user
        String roleName = authResult.isAdmin ? "ROLE_SUPER" : "ROLE_USER";
        Role role = new Role();
        role.setName(br.gov.pb.der.netnotify.enums.RoleName.valueOf(roleName));
        User user = new User();
        user.setUsername(loginUserDto.getUsername());
        user.setPassword(loginUserDto.getPassword());
        user.setRoles(List.of(role));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String token = jwtService.generateToken(userDetails);
        RecoveryJwtTokenDto dto = new RecoveryJwtTokenDto(token);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

}