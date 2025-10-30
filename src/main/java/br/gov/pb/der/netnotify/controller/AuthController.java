package br.gov.pb.der.netnotify.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.AuthCallbackRequest;
import br.gov.pb.der.netnotify.dto.KeycloakTokenResponse;
import br.gov.pb.der.netnotify.dto.RefreshTokenRequest;
import br.gov.pb.der.netnotify.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Controlador de autenticação
 * 
 * Responsável por:
 * 1. Receber código de autorização do Keycloak (do frontend)
 * 2. Trocar código por token JWT
 * 3. Retornar token e dados do usuário para o frontend
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint para troca de código por token
     * 
     * POST /api/auth/callback
     * 
     * Request:
     * {
     * "code": "xxxxx"
     * }
     * 
     * Response:
     * {
     * "access_token": "jwt-token",
     * "refresh_token": "refresh-token",
     * "expires_in": 3600,
     * "user": {
     * "id": "user-id",
     * "email": "user@example.com",
     * "name": "User Name"
     * }
     * }
     */

    @GetMapping("/teste")
    public ResponseEntity<String> getMethodName() {
        return ResponseEntity.ok("teste");
    }
    

    @PostMapping("/callback")
    public ResponseEntity<KeycloakTokenResponse> callback(
        @RequestBody AuthCallbackRequest request,
        @RequestParam(name = "redirect_uri", required = false) String redirectUriParam,
        @RequestHeader(name = "X-Redirect-Uri", required = false) String redirectUriHeader) {
        try {
            log.info("🔄 Recebido callback com código: {}",
                    request.getCode() != null ? request.getCode().substring(0, 20) + "..." : "null");
        log.info("📍 redirectUri recebido do cliente (body): {}", request.getRedirectUri());
        log.info("📍 redirectUri recebido como query param: {}", redirectUriParam);
        log.info("📍 redirectUri recebido no header X-Redirect-Uri: {}", redirectUriHeader);

            if (request.getCode() == null || request.getCode().isEmpty()) {
                log.error("❌ Código não fornecido");
                return ResponseEntity.badRequest().build();
            }

            // Fallback chain para obter o redirectUri efetivo enviado pelo frontend
            String clientRedirect = firstNonBlank(request.getRedirectUri(), redirectUriParam, redirectUriHeader);

            // Troca código por token
            KeycloakTokenResponse response = authService.exchangeCodeForToken(request.getCode(), clientRedirect);

            log.info("✅ Callback processado com sucesso para usuário: {}",
                    response.getUser() != null ? response.getUser().getEmail() : "unknown");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erro ao processar callback:", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    /**
     * Endpoint para renovar token
     * 
     * POST /api/auth/refresh
     * 
     * Request:
     * {
     * "refresh_token": "xxxxx"
     * }
     * 
     * Response:
     * {
     * "access_token": "new-jwt-token",
     * "refresh_token": "new-refresh-token",
     * "expires_in": 3600,
     * "user": { ... }
     * }
     */
    @PostMapping("/refresh")
    public ResponseEntity<KeycloakTokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            log.info("🔄 Renovando token...");

            if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
                log.error("❌ Refresh token não fornecido");
                return ResponseEntity.badRequest().build();
            }

            KeycloakTokenResponse response = authService.refreshAccessToken(request.getRefreshToken());

            log.info("✅ Token renovado com sucesso");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token:", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("OK", "Auth service is running"));
    }

    /**
     * DTO para response de health
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private String message;
    }

    /**
     * Endpoint para diagnóstico da configuração Keycloak
     * Útil para debugar problemas de redirect_uri
     */
    @GetMapping("/config-debug")
    public ResponseEntity<Map<String, String>> configDebug() {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("NOTE", "This endpoint shows configuration for debugging purposes only. Do NOT expose in production!");
        config.put("KEYCLOAK_TOKEN_URL", System.getenv("KEYCLOAK_TOKEN_URL"));
        config.put("KEYCLOAK_CLIENT_ID", System.getenv("KEYCLOAK_CLIENT_ID"));
        config.put("KEYCLOAK_REDIRECT_URI", System.getenv("KEYCLOAK_REDIRECT_URI"));
        config.put("KEYCLOAK_AUTH_SERVER_URL", System.getenv("KEYCLOAK_AUTH_SERVER_URL"));
        config.put("KEYCLOAK_REALM", System.getenv("KEYCLOAK_REALM"));
        config.put("MESSAGE", "Verify that KEYCLOAK_REDIRECT_URI matches exactly what's registered in Keycloak Admin Console > Clients > teste-cli > Valid Redirect URIs");
        return ResponseEntity.ok(config);
    }
}
