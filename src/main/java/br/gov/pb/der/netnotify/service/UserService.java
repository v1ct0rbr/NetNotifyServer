package br.gov.pb.der.netnotify.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.auth.UserDetailsImpl;
import br.gov.pb.der.netnotify.dto.LoginUserDto;
import br.gov.pb.der.netnotify.dto.RecoveryJwtTokenDto;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    // Método responsável por autenticar um usuário e retornar um token JWT
    public RecoveryJwtTokenDto authenticateUser(LoginUserDto loginUserDto) {
        // Cria um objeto de autenticação com o email e a senha do usuário
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                loginUserDto.getUsername(), loginUserDto.getPassword());

        // // Autentica o usuário com as credenciais fornecidas
        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

        // // Obtém o objeto UserDetails do usuário autenticado
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        // User userDetails = findByUsernameAndPassword(loginUserDto.getUsername(),
        // loginUserDto.getPassword());

        // Gera um token JWT para o usuário autenticado
        return new RecoveryJwtTokenDto(jwtTokenService.generateToken(userDetails));
    }

    public User getLoggedUser() {
        User user = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
            user = userDetails.getUser(); // ou getUserEntity(), dependendo da sua implementação
            if (user != null) {
                user.setPassword(null);
            }
        }
        return user;

    }

    @Cacheable(value = "users", key = "#username")
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).get();
    }

}
