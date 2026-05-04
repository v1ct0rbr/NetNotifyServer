package br.gov.pb.der.netnotify.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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

import br.gov.pb.der.netnotify.filter.AgentApiTokenFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    private final AgentApiTokenFilter agentApiTokenFilter;

    @Value("${app.keycloak.client-id:netnotify-client}")
    private String resource;

    @Value("${app.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:not-configured}")
    private String issuerUri;

    @Value("${app.keycloak.roles.user:NETNOTIFY1}")
    private String roleUser;

    @Value("${app.keycloak.roles.admin:NETNOTIFY2}")
    private String roleAdmin;

    /**
     * Filter chain dedicada para /agent-api/** — sem oauth2ResourceServer,
     * evitando que o BearerTokenAuthenticationFilter tente parsear o token
     * do agente como JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain agentApiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/agent-api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(agentApiTokenFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/messages/**", "/dashboard/**").hasAnyRole(roleUser, roleAdmin)
                .requestMatchers("/aux/**").authenticated()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/profile/**").authenticated()
                .requestMatchers("/admin/**").hasAnyRole(roleAdmin)
                .requestMatchers("/rabbit/**").hasAnyRole(roleAdmin)
                .requestMatchers(HttpMethod.DELETE, "/messages/**").hasAnyRole(roleAdmin)
                .requestMatchers("/departments/**").hasAnyRole(roleAdmin)
                .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            logger.error("🔐 [AUTH_ERROR] Autenticação falhou na requisição: {} {}", req.getMethod(),
                                    req.getRequestURI());
                            logger.error("    Authorization header presente: {}",
                                    req.getHeader("Authorization") != null);
                            logger.error("    Erro: {}", ex.getMessage());
                            res.sendError(401, "Unauthorized");
                        }));
        return http.build();
    }

    private void extractRolesFromClaim(Jwt jwt, String claimName, Collection<GrantedAuthority> authorities) {
        extractRolesFromClaim(jwt, claimName, authorities, null);
    }

    @SuppressWarnings("unchecked")
    private void extractRolesFromClaim(Jwt jwt, String claimName, Collection<GrantedAuthority> authorities,
            String resource) {
        Map<String, Object> claim = jwt.getClaim(claimName);
        logger.info("🔍 Extraindo roles do claim: {} - Claim exists: {}", claimName, claim != null);

        if (claim != null && claim.containsKey("roles")) {
            List<String> roles = (List<String>) claim.get("roles");
            logger.info("✅ Roles encontradas em {}: {}", claimName, roles);
            for (String role : roles) {
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                }
            }
        } else if (claim != null) {
            logger.warn("⚠️ Claim {} não contém a chave 'roles'. Conteúdo do claim: {}", claimName, claim.keySet());
        }

        if (resource != null) {
            // FIX: Usar "resource_access" ao invés de claimName
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            logger.info("🔍 Extraindo roles de resource_access para recurso: {} - Exists: {}", resource,
                    resourceAccess != null);

            if (resourceAccess != null && resourceAccess.containsKey(resource)) {
                Map<String, Object> resourceRoles = (Map<String, Object>) resourceAccess.get(resource);
                if (resourceRoles != null && resourceRoles.containsKey("roles")) {
                    List<String> roles = (List<String>) resourceRoles.get("roles");
                    logger.info("✅ Roles encontradas para recurso {}: {}", resource, roles);
                    for (String role : roles) {
                        if (role != null) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                        }
                    }
                } else {
                    logger.warn("⚠️ Recurso {} não contém roles. Conteúdo: {}", resource, resourceRoles);
                }
            } else if (resourceAccess != null) {
                logger.warn("⚠️ Recurso {} não encontrado em resource_access. Recursos disponíveis: {}", resource,
                        resourceAccess.keySet());
            }
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // Parse das origens permitidas do arquivo de configuração
        // Formato esperado:
        // "http://localhost:3000,https://app.example.com,http://localhost:5173/*"
        List<String> allowedOriginsList = List.of(allowedOrigins.split(","));
        // Processa cada origem: remove espaços em branco e wildcards no final
        List<String> processedOrigins = new ArrayList<>();
        for (String origin : allowedOriginsList) {
            String processed = origin.trim()
                    .replaceAll("/\\*$", "") // Remove /* do final
                    .replaceAll("\\*$", ""); // Remove * solto do final
            if (!processed.isEmpty()) {
                processedOrigins.add(processed);
                System.out.println("✅ CORS Allowed Origin: " + processed);
            }
        }
        // Se nenhuma origem válida foi configurada, permite localhost por padrão
        if (processedOrigins.isEmpty()) {
            processedOrigins.add("http://localhost:3000");
            System.out.println("⚠️ Nenhuma origem CORS configurada - usando localhost:3000 como padrão");
        }
        corsConfiguration.setAllowedOrigins(processedOrigins);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE",
                "OPTIONS", "PATCH"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setExposedHeaders(List.of("Authorization",
                "Content-Type"));
        corsConfiguration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        // NÃO validar o "aud" (audience) pois o Keycloak pode usar valores diferentes
        jwtAuthenticationConverter.setPrincipalClaimName("preferred_username");

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            logger.info("🔐 ============ INICIANDO EXTRAÇÃO DE ROLES DO JWT ============");
            logger.info("📋 Cliente (aud): {}", jwt.getAudience());
            logger.info("👤 Subject (sub): {}", jwt.getSubject());
            // Use dedicated accessor to avoid ClassCastException from String.valueOf
            // overloads
            logger.info("👤 Preferred Username: {}", jwt.getClaimAsString("preferred_username"));
            logger.info("🔑 Resource configurado: {}", resource);
            logger.info("🌐 Issuer esperado: {}", issuerUri);
            logger.info("🌐 Issuer no token: {}", jwt.getIssuer());
            logger.info("📝 Claims disponíveis no JWT: {}", jwt.getClaims().keySet());
            logger.info("📅 Token emitido em: {}", jwt.getIssuedAt());
            logger.info("📅 Token expira em: {}", jwt.getExpiresAt());

            extractRolesFromClaim(jwt, "realm_access", authorities);
            extractRolesFromClaim(jwt, "resource_access", authorities, resource);

            logger.info("✨ TOTAL DE ROLES EXTRAÍDAS: {}", authorities.size());
            logger.info("📊 Authorities finais: {}", authorities);
            logger.info("🔐 ============ FIM DA EXTRAÇÃO DE ROLES ============");

            return authorities;
        });

        return jwtAuthenticationConverter;
    }

}
