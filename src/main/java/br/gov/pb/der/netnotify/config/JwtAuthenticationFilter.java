package br.gov.pb.der.netnotify.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.gov.pb.der.netnotify.auth.UserDetailsImpl;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.repository.UserRepository;
import br.gov.pb.der.netnotify.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtTokenService; // Service que definimos anteriormente

    private final UserRepository userRepository; // Repository que definimos anteriormente

    public static final String[] ENDPOINTS_WITH_AUTHENTICATION_NOT_REQUIRED = {
        "/auth",
        "/public",
        "/hello", // Exemplo de endpoint público
    // para
    // fazer login
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            if (checkIfEndpointIsNotPublic(request)) {
                String token = recoveryToken(request);
                if (token == null || token.isEmpty()) {
                    throw new RuntimeException("Token not found or is empty");
                }
                // Valida o token antes de extrair o subject
                if (!jwtTokenService.isTokenValid(token)) {
                    throw new RuntimeException("Invalid or expired token");
                }
                String subject = jwtTokenService.getSubjectFromToken(token);
                Optional<User> userOptional = userRepository.findByUsername(subject);
                if (userOptional.isEmpty()) {
                    throw new RuntimeException("User not found");
                }

                UserDetailsImpl userDetails = new UserDetailsImpl(userOptional.get());
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Se o endpoint é público, não faz nada
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    // Recupera o token do cabeçalho Authorization da requisição
    private String recoveryToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            return authorizationHeader.replace("Bearer ", "");
        }
        return null;
    }

    // Verifica se o endpoint requer autenticação antes de processar a requisição
    private boolean checkIfEndpointIsNotPublic(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        boolean isPublicEndpoint = Arrays.stream(ENDPOINTS_WITH_AUTHENTICATION_NOT_REQUIRED)
                .anyMatch(endpoint -> requestURI.startsWith(endpoint));
        // se o endpoint é do tipo /api/auth/** ou /api/public/**, não requer
        // autenticação
        return !isPublicEndpoint;
    }

}
