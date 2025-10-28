package br.gov.pb.der.netnotify.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.gov.pb.der.netnotify.security.CustomLogoutHandler;
import br.gov.pb.der.netnotify.security.CustomOidcUserService;
import br.gov.pb.der.netnotify.security.KeycloakJwtAuthenticationConverter;
import br.gov.pb.der.netnotify.security.KeycloakLogoutSuccessHandler;
import br.gov.pb.der.netnotify.security.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    private final KeycloakJwtAuthenticationConverter keycloakJwtConverter;
    private final CustomLogoutHandler customLogoutHandler;
    private final KeycloakLogoutSuccessHandler logoutSuccessHandler;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakJwtConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureUrl("/auth/login?error=true")
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)))
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .addLogoutHandler(customLogoutHandler)
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/public/**", "/error").permitAll()
                        .requestMatchers("/aux/**", "/messages/**", "/notify/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/me/**", "/profile/**").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());

        return http.build();
    }

    // Configuração CORS (importante para evitar problemas 403 em requisições de
    // diferentes origens)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*")); // Permite todas origens (ajuste para produção)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
