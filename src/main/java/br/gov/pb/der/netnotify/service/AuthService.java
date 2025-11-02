package br.gov.pb.der.netnotify.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import br.gov.pb.der.netnotify.dto.KeycloakTokenResponse;
import br.gov.pb.der.netnotify.dto.UserInfo;
import br.gov.pb.der.netnotify.security.KeycloakUser;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de autenticação
 * 
 * Responsável por:
 * 1. Trocar código de autorização por token do Keycloak
 * 2. Extrair informações do usuário do token
 * 3. Criar JWT customizado
 * 4. Renovar tokens usando refresh token
 */
@Service
@Slf4j
public class AuthService {

    @Value("${app.keycloak.client-id}")
    private String clientId;

    @Value("${app.keycloak.client-secret}")
    private String clientSecret;

    @Value("${app.keycloak.token-url}")
    private String tokenUrl;

    @Value("${app.keycloak.redirect-uri}")
    private String configuredRedirectUri;

    @Value("${app.jwt.secret:seu-secret-jwt-super-seguro-aqui}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:3600}")
    private Long jwtExpiration;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Troca código de autorização por token
     * 
     * @param code              - Código retornado pelo Keycloak
     * @param clientRedirectUri - redirect_uri recebido do frontend (deve ser igual
     *                          ao usado na autorização)
     * @return KeycloakTokenResponse com token e dados do usuário
     */
    public KeycloakTokenResponse exchangeCodeForToken(String code, String clientRedirectUri, String codeVerifier)
            throws Exception {
        log.info("🔄 Trocando código por token do Keycloak...");

        try {
            // Step 1: Faz requisição ao Keycloak para trocar código por token
            String effectiveRedirect = selectEffectiveRedirectUri(clientRedirectUri);
            Map<String, Object> keycloakToken = this.getKeycloakToken(code, effectiveRedirect, codeVerifier);

            if (keycloakToken == null) {
                throw new Exception("Falha ao obter token do Keycloak");
            }

            String keycloakAccessToken = (String) keycloakToken.get("access_token");
            String refreshToken = (String) keycloakToken.get("refresh_token");
            Integer expiresIn = (Integer) keycloakToken.get("expires_in");

            log.info("✅ Token do Keycloak obtido");

            // Step 2: Extrai informações do usuário do token do Keycloak
            KeycloakUser user = this.extractUserFromToken(keycloakAccessToken);

            // Step 3: Cria JWT customizado
            String jwtToken = this.createJwtToken(user);

            log.info("✅ JWT customizado criado para usuário: {}", user.getEmail());

            // Step 4: Monta resposta
            KeycloakTokenResponse response = KeycloakTokenResponse.builder()
                    .accessToken(jwtToken)
                    .refreshToken(refreshToken)
                    .expiresIn((long) expiresIn)
                    .tokenType("Bearer")
                    .user(new UserInfo(user))
                    .build();

            return response;
        } catch (Exception e) {
            log.error("❌ Erro na troca de código:", e);
            throw e;
        }
    }

    /**
     * Renova token usando refresh token
     */
    public KeycloakTokenResponse refreshAccessToken(String refreshToken) throws Exception {
        log.info("🔄 Renovando token com refresh token...");

        try {
            // Faz requisição ao Keycloak para renovar token
            Map<String, Object> keycloakToken = this.refreshKeycloakToken(refreshToken);

            if (keycloakToken == null) {
                throw new Exception("Falha ao renovar token do Keycloak");
            }

            String newAccessToken = (String) keycloakToken.get("access_token");
            String newRefreshToken = (String) keycloakToken.get("refresh_token");
            Integer expiresIn = (Integer) keycloakToken.get("expires_in");

            log.info("✅ Token do Keycloak renovado");

            // Extrai informações do usuário
            KeycloakUser user = this.extractUserFromToken(newAccessToken);

            // Cria novo JWT customizado
            String jwtToken = this.createJwtToken(user);

            KeycloakTokenResponse response = KeycloakTokenResponse.builder()
                    .accessToken(jwtToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn((long) expiresIn)
                    .tokenType("Bearer")
                    .user(new UserInfo(user))
                    .build();

            return response;
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token:", e);
            throw e;
        }
    }

    /**
     * Faz requisição ao Keycloak para trocar código por token
     */
    private Map<String, Object> getKeycloakToken(String code, String effectiveRedirectUri, String codeVerifier)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Encode redirect_uri explicitamente

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", effectiveRedirectUri); // Spring já faz encoding automaticamente no form-urlencoded
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            body.add("code_verifier", codeVerifier.trim());
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("🔄 Trocando authorization code por token:");
            log.info("  🔗 Token URL: {}", tokenUrl);
            log.info("  📍 Redirect URI (original): {}", effectiveRedirectUri);

            log.info("  🔑 Client ID: {}", clientId);
            if (codeVerifier != null && !codeVerifier.isBlank()) {
                log.info("  🔐 PKCE code_verifier: presente (tamanho={})", codeVerifier.length());
            } else {
                log.info("  🔐 PKCE code_verifier: ausente");
            }
            log.debug(
                    "  📦 Request Body: grant_type=authorization_code, client_id={}, code=***, redirect_uri={}, code_verifier={}",
                    clientId, effectiveRedirectUri,
                    (codeVerifier != null && !codeVerifier.isBlank()) ? "***" : "<none>");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null) {
                log.error("❌ Resposta nula do Keycloak");
                return null;
            }
            for (String key : response.keySet()) {
                // mostrar todos os valores retornados
                log.info("Received response {}: {}", key, response.get(key));
            }

            log.info("✅ Token obtido com sucesso do Keycloak");
            return response;
        } catch (Exception e) {
            log.error("❌ ERRO ao chamar Keycloak!");
            log.error("  URL tentada: {}", tokenUrl);
            log.error("  Redirect URI enviado (original): {}", effectiveRedirectUri);

            log.error("  Mensagem erro: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Seleciona o redirect_uri efetivo: prioriza o enviado pelo cliente, se válido.
     * Se vazio ou inválido, cai para o configurado no servidor.
     */
    private String selectEffectiveRedirectUri(String clientRedirectUri) {
        // Se o cliente enviou um redirectUri, use-o. Ele precisa ser exatamente o mesmo
        // usado na autorização.
        if (clientRedirectUri != null && !clientRedirectUri.isBlank()) {
            log.info("📥 redirectUri recebido do cliente será utilizado: {}", clientRedirectUri);
            return clientRedirectUri.trim();
        }
        log.info("ℹ️ redirectUri do cliente ausente; usando o configurado no servidor: {}", configuredRedirectUri);
        return configuredRedirectUri;
    }

    /**
     * Faz requisição ao Keycloak para renovar token
     */
    private Map<String, Object> refreshKeycloakToken(String refreshToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "aut");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null) {
                log.error("❌ Resposta nula ao renovar token");
                return null;
            }

            log.info("✅ Token renovado com sucesso");
            return response;
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token do Keycloak:", e);
            throw e;
        }
    }

    /**
     * Extrai informações do usuário do JWT do Keycloak
     * 
     * Decodifica o token sem validação (já foi validado pelo Keycloak)
     */
    private KeycloakUser extractUserFromToken(String token) throws Exception {
        try {
            // Decodifica token (sem validação, pois já foi validado pelo Keycloak)
            var decodedJWT = JWT.decode(token);
            String email = decodedJWT.getClaim("email").asString();
            String name = decodedJWT.getClaim("name").asString();
            String givenName = decodedJWT.getClaim("given_name").asString();
            String familyName = decodedJWT.getClaim("family_name").asString();
            String sub = decodedJWT.getClaim("sub").asString();
            Map<String, Object> realmAccess = decodedJWT.getClaim("realm_access").asMap();
            List<String> roles;
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List) {
                    roles = ((List<?>) rolesObj).stream()
                            .filter(java.util.Objects::nonNull)
                            .map(Object::toString)
                            .collect(Collectors.toList());
                } else {
                    roles = Collections.emptyList();
                }
            } else {
                roles = Collections.emptyList();
            }
            KeycloakUser user = KeycloakUser.builder()
                    .id(sub)
                    .username(email)
                    .email(email)
                    .firstName(givenName)
                    .lastName(familyName)
                    .enabled(true)
                    .emailVerified(true)
                    .authorities(roles.stream()
                            .map(role -> "ROLE_" + role.toUpperCase())
                            .map(roleName -> (GrantedAuthority) () -> roleName)
                            .collect(Collectors.toSet()))
                    .build();

            return user;
        } catch (Exception e) {
            log.error("❌ Erro ao extrair usuário do token:", e);
            throw e;
        }
    }

    /**
     * Cria JWT customizado
     */
    private String createJwtToken(KeycloakUser user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Instant now = Instant.now();
            Instant expiresAt = now.plus(jwtExpiration, ChronoUnit.SECONDS);

            String token = JWT.create()
                    .withSubject(user.getId())
                    .withClaim("email", user.getEmail())
                    .withClaim("name", user.getFullName())
                    .withClaim("given_name", user.getFirstName())
                    .withClaim("family_name", user.getLastName())
                    .withIssuedAt(Date.from(now))
                    .withExpiresAt(Date.from(expiresAt))
                    .withIssuer("netnotify-backend")
                    .sign(algorithm);

            log.info("✅ JWT criado com sucesso para: {}", user.getEmail());
            return token;
        } catch (Exception e) {
            log.error("❌ Erro ao criar JWT:", e);
            throw e;
        }
    }

    /**
     * Revoga um token (refresh token) no Keycloak
     * Invalida a sessão do usuário
     * 
     * @param refreshToken - Refresh token a ser revogado
     * @return true se revogado com sucesso, false caso contrário
     */
    public boolean revokeToken(String refreshToken) throws Exception {
        log.info("🔄 Revogando refresh token no Keycloak...");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("token", refreshToken);
            body.add("token_type_hint", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Constrói URL de revogação: substituir /token por /revoke
            String revokeUrl = tokenUrl.replace("/token", "/revoke");

            log.info("🔗 URL de revogação: {}", revokeUrl);

            restTemplate.postForEntity(revokeUrl, request, Void.class);

            log.info("✅ Token revogado com sucesso");
            return true;

        } catch (Exception e) {
            log.error("❌ Erro ao revogar token no Keycloak: {}", e.getMessage(), e);
            // Retorna false mas não lança exceção, pois falha não deve impedir logout local
            return false;
        }
    }

    /**
     * Faz logout do usuário revogando todos os tokens associados
     * Equivalente ao logout no Keycloak
     * 
     * @param refreshToken - Refresh token do usuário
     * @return true se logout bem-sucedido
     */
    public boolean logout(String refreshToken) throws Exception {
        log.info("🚪 Realizando logout...");

        try {
            // Tenta revogar o refresh token
            boolean revoked = this.revokeToken(refreshToken);

            if (revoked) {
                log.info("✅ Logout realizado com sucesso");
                return true;
            } else {
                log.warn("⚠️ Logout local bem-sucedido, mas token não foi revogado no Keycloak");
                return true; // Retorna true pois logout local foi bem-sucedido
            }

        } catch (Exception e) {
            log.error("❌ Erro ao fazer logout: {}", e.getMessage(), e);
            throw e;
        }
    }
}
