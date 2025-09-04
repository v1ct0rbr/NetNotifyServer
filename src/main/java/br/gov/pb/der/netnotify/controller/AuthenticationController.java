package br.gov.pb.der.netnotify.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.LoginUserDto;
import br.gov.pb.der.netnotify.dto.RecoveryJwtTokenDto;
import br.gov.pb.der.netnotify.service.UserService;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<RecoveryJwtTokenDto> authenticateUser(@RequestBody LoginUserDto loginUserDto) {
        RecoveryJwtTokenDto token = userService.authenticateUser(loginUserDto);       

        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    

}