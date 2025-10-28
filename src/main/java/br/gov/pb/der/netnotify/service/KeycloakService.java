package br.gov.pb.der.netnotify.service;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import br.gov.pb.der.netnotify.dto.KeycloakTokenResponse;
import br.gov.pb.der.netnotify.dto.RecoveryJwtTokenDto;
import br.gov.pb.der.netnotify.dto.UserInfo;
import br.gov.pb.der.netnotify.model.User;
import br.gov.pb.der.netnotify.repository.UserRepository;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class KeycloakService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${keycloak.server-url:https://keycloak.derpb.com.br}")
    private String keycloakUrl;

    @Value("${keycloak.realm:testes}")
    private String realm;

    @Value("${keycloak.client-id:netnotify-frontend}")
    private String clientId;

    @Value("${keycloak.client-secret:your-secret}")
    private String clientSecret;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /**
     * Exchange do authorization code do Keycloak por um JWT customizado
     */
    public RecoveryJwtTokenDto exchangeAuthorizationCode(
            String code,
            String state,
            String sessionState,
            String redirectUri
    ) throws Exception {
        log.info("Iniciando exchange do authorization code do Keycloak");

        // 1. Faz o token request para o Keycloak
        KeycloakTokenResponse tokenResponse = getTokenFromKeycloak(code, redirectUri);
        
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("Falha ao obter token do Keycloak");
        }

        log.info("Token obtido do Keycloak com sucesso");

        // 2. Extrai informações do access token do Keycloak (normalmente é um JWT)
        Map<String, Object> keycloakUserInfo = extractUserInfoFromToken(tokenResponse.getAccessToken());
        
        String username = (String) keycloakUserInfo.get("preferred_username");
        String email = (String) keycloakUserInfo.get("email");
        String fullName = (String) keycloakUserInfo.get("name");

        // 3. Sincroniza com banco de dados local (ou cria novo usuário)
        User user = syncUserWithKeycloak(username, email, fullName);


        // 4. Gera JWT customizado para a aplicação
        String jwtToken = generateCustomJWT(user);

        log.info("JWT gerado com sucesso para usuário: {}", username);

        return new RecoveryJwtTokenDto(jwtToken, new UserInfo(user));
    }

    /**
     * Faz o token request para o Keycloak
     */
    private KeycloakTokenResponse getTokenFromKeycloak(String code, String redirectUri) {
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", code);
            body.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("Fazendo token request para: {}", tokenUrl);
            
            KeycloakTokenResponse response = restTemplate.postForObject(
                tokenUrl,
                request,
                KeycloakTokenResponse.class
            );

            return response;
        } catch (Exception e) {
            log.error("Erro ao fazer token request com Keycloak", e);
            throw new RuntimeException("Falha ao fazer token request com Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Extrai informações do user do access token do Keycloak (simples decode, sem validação)
     */
    private Map<String, Object> extractUserInfoFromToken(String accessToken) throws Exception {
        try {
            // Para este exemplo, fazemos uma chamada ao endpoint userinfo do Keycloak
            // Ou podemos decodificar o JWT (se for um JWT)
            
            String userInfoUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.debug("Fazendo userinfo request para: {}", userInfoUrl);

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.postForObject(
                userInfoUrl,
                request,
                Map.class
            );

            return userInfo != null ? userInfo : new HashMap<>();
        } catch (Exception e) {
            log.warn("Erro ao obter userinfo, tentando decodificar token", e);
            // Fallback: tenta decodificar o JWT
            return decodeJWT(accessToken);
        }
    }

    /**
     * Simples decodificação de JWT (sem validação de assinatura, apenas para exemplo)
     */
    private Map<String, Object> decodeJWT(String token) throws Exception {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Token inválido");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            // Aqui você poderia usar um JSON parser como Jackson
            // Por simplificidade, estamos retornando um mapa vazio
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Erro ao decodificar JWT", e);
            throw e;
        }
    }

    /**
     * Sincroniza usuário com banco de dados local
     */
    private User syncUserWithKeycloak(String username, String email, String fullName) {
        User user = userRepository.findByUsername(username)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setFullName(fullName);                
                return newUser;
            });

        // Atualiza informações do usuário
        user.setEmail(email);
        user.setFullName(fullName);
        

        return userRepository.save(user);
    }

    /**
     * Gera JWT customizado para a aplicação
     */
    public String generateCustomJWT(User user) throws Exception {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiresAt = new Date(nowMillis + 3600000); // 1 hora

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(user.getUsername())
            .issuer("netnotify")            
            .expirationTime(expiresAt)
            .claim("userId", user.getId())
            .claim("email", user.getEmail())
            .claim("fullName", user.getFullName())
            .claim("roles", user.getApplicationRoles().stream()
                .map(User.ApplicationRole::name)
                .toList())
            .build();

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        SignedJWT jwt = new SignedJWT(header, claims);

        // Aqui você precisaria implementar a assinatura com HMAC-SHA256
        // Por simplificidade, use uma biblioteca como:
        // jwt.sign(new MACSigner(jwtSecret.getBytes()));

        return jwt.serialize();
    }
}
