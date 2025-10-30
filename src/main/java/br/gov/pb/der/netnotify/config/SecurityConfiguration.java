package br.gov.pb.der.netnotify.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {


    @Value("${keycloak.resource:your-client-id}")
    private String resource;

    @Value("${app.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/messages/**").hasAuthority("ROLE_USERS")
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
        )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    private void extractRolesFromClaim(Jwt jwt, String claimName, Collection<GrantedAuthority> authorities) {
        extractRolesFromClaim(jwt, claimName, authorities, null);
    }

    @SuppressWarnings("unchecked")
    private void extractRolesFromClaim(Jwt jwt, String claimName, Collection<GrantedAuthority> authorities, String resource) {
        Map<String, Object> claim = jwt.getClaim(claimName);
        if (claim != null && claim.containsKey("roles")) {
            List<String> roles = (List<String>) claim.get("roles");
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }

        if (resource != null) {
            Map<String, Object> resourceAccess = jwt.getClaim(claimName);
            if (resourceAccess != null && resourceAccess.containsKey(resource)) {
                List<String> resourceRoles = (List<String>) ((Map<String, Object>) resourceAccess.get(resource)).get("roles");
                if (resourceRoles != null) {
                    for (String role : resourceRoles) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
        }
    }

    // Configuração CORS (importante para evitar problemas 403 em requisições de
    // diferentes origens)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        List<String> allowedOriginsList = List.of(allowedOrigins.split(","));
        for (String origin : allowedOriginsList) {
            System.out.println("Allowed Origin: " + origin);
        }
        corsConfiguration.setAllowedOrigins(allowedOriginsList);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            extractRolesFromClaim(jwt, "realm_access", authorities);
            extractRolesFromClaim(jwt, "resource_access", authorities, resource);

            return authorities;
        });

        return jwtAuthenticationConverter;
    }

}
