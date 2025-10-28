package br.gov.pb.der.netnotify.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import br.gov.pb.der.netnotify.auth.UserDetailsImpl;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.service.JwtService;
import br.gov.pb.der.netnotify.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler customizado para redirecionar após login OAuth2 bem-sucedido
 * Gera JWT e redireciona diretamente para a aplicação React
 */
@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.react-app-url}")
    private String reactAppUrl;

    private final UserService userService;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        log.info("OAuth2 Authentication successful for user: {} - generating JWT and redirecting to React",
                authentication.getName());

        try {
            // Obter ou criar usuário local integrado com Keycloak
            User localUser = userService.getOrCreateUser();

            if (localUser == null) {
                log.error("Falha ao obter dados do usuário após autenticação OAuth2");
                response.sendRedirect(reactAppUrl + "?error=user_not_found");
                return;
            }

            // Gerar JWT usando JwtService baseado no usuário local
            UserDetailsImpl userDetails = new UserDetailsImpl(localUser);
            String jwtToken = jwtService.generateToken(userDetails);

            log.info("Token JWT gerado com sucesso para usuário: {}", localUser.getUsername());

            // Construir URL de redirect para o app React com token
            String redirectUrl = buildReactAppUrl(jwtToken);

            log.info("Redirecionando para app React após OAuth2 login");

            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("Erro ao processar autenticação OAuth2: {}", e.getMessage(), e);
            response.sendRedirect(reactAppUrl + "?error=authentication_error");
        }
    }

    private String buildReactAppUrl(String jwtToken) {
        StringBuilder url = new StringBuilder(reactAppUrl);

        // Adicionar rota se a URL não terminar com /
        if (!url.toString().endsWith("/")) {
            url.append("/");
        }

        // Adicionar token como parâmetro
        url.append("?token=").append(jwtToken);

        return url.toString();
    }
}
