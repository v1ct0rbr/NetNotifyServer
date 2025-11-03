package br.gov.pb.der.netnotify.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.AuthCallbackRequest;
import br.gov.pb.der.netnotify.dto.KeycloakTokenResponse;
import br.gov.pb.der.netnotify.dto.RefreshTokenRequest;
import br.gov.pb.der.netnotify.service.AuthService;
import br.gov.pb.der.netnotify.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador de autenticação
 *
 * Responsável por: 1. Receber código de autorização do Keycloak (do frontend)
 * 2. Trocar código por token JWT 3. Retornar token e dados do usuário para o
 * frontend
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    /**
     * Endpoint para troca de código por token
     *
     * POST /api/auth/callback
     *
     * Request: { "code": "xxxxx" }
     *
     * Response: { "access_token": "jwt-token", "refresh_token":
     * "refresh-token", "expires_in": 3600, "user": { "id": "user-id", "email":
     * "user@example.com", "name": "User Name" } }
     */
    @GetMapping("/teste")
    public ResponseEntity<String> getMethodName() {
        return ResponseEntity.ok("teste");
    }

    /* @GetMapping("/whoami")
    public ResponseEntity<UserInfo> whoAmI() {
        User user = userService.getLoggedUser();
    UserInfo userInfo = new UserInfo(user);
        return ResponseEntity.ok(userInfo);
    } */

    @PostMapping(value = "/callback", produces = "application/json")
    public ResponseEntity<KeycloakTokenResponse> callback(
            @RequestBody AuthCallbackRequest request) {
        try {
            String redirectUriParam = request.getRedirect_uri();

            log.info("🔄 Recebido callback com código: {}",
                    request.getCode() != null ? request.getCode().substring(0, 20) + "..." : "null");

            log.info("📍 redirectUri recebido do cliente (body): {}", request.getRedirect_uri());
            log.info("📍 redirectUri recebido como query param: {}", redirectUriParam);

            if (request.getCode_verifier() != null && !request.getCode_verifier().isBlank()) {
                log.info("🔐 PKCE code_verifier recebido do cliente (tamanho={}): OK",
                        request.getCode_verifier().length());
            } else {
                log.info("🔐 PKCE code_verifier não enviado pelo cliente (prossegue sem PKCE)");
            }

            if (request.getCode() == null || request.getCode().isEmpty()) {
                log.error("❌ Código não fornecido");
                return ResponseEntity.badRequest().build();
            }

            // Fallback chain para obter o redirectUri efetivo enviado pelo frontend

            // Troca código por token (inclui code_verifier quando enviado)
            KeycloakTokenResponse response = authService.exchangeCodeForToken(request.getCode(), redirectUriParam,
                    request.getCode_verifier());

            log.info("✅ Callback processado com sucesso para usuário: {}",
                    response.getUser() != null ? response.getUser().getEmail() : "unknown");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erro ao processar callback:", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * Endpoint para renovar token
     *
     * POST /api/auth/refresh
     *
     * Request: { "refresh_token": "xxxxx" }
     *
     * Response: { "access_token": "new-jwt-token", "refresh_token":
     * "new-refresh-token", "expires_in": 3600, "user": { ... } }
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
     * Endpoint para logout e revogação de token
     *
     * POST /auth/logout
     *
     * Request: { "refresh_token": "xxxxx" }
     *
     * Response: { "success": true, "message": "Logout realizado com sucesso" }
     */
    @PostMapping(value = "/logout", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> logout(@RequestBody RefreshTokenRequest request) {
        try {
            log.info("🚪 Processando logout...");

            if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
                log.warn("⚠️ Refresh token não fornecido para logout");
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", true);
                response.put("message", "Logout realizado localmente");
                return ResponseEntity.ok(response);
            }

            boolean success = authService.logout(request.getRefreshToken());

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Logout realizado com sucesso"
                    : "Logout local realizado, mas erro ao revogar token no Keycloak");

            log.info("✅ Logout bem-sucedido");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro ao fazer logout: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Erro ao fazer logout: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
     * Endpoint para diagnóstico da configuração Keycloak Útil para debugar
     * problemas de redirect_uri
     */
    @GetMapping("/config-debug")
    public ResponseEntity<Map<String, String>> configDebug() {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("NOTE",
                "This endpoint shows configuration for debugging purposes only. Do NOT expose in production!");
        config.put("KEYCLOAK_TOKEN_URL", System.getenv("KEYCLOAK_TOKEN_URL"));
        config.put("KEYCLOAK_CLIENT_ID", System.getenv("KEYCLOAK_CLIENT_ID"));
        config.put("KEYCLOAK_REDIRECT_URI", System.getenv("KEYCLOAK_REDIRECT_URI"));
        config.put("KEYCLOAK_AUTH_SERVER_URL", System.getenv("KEYCLOAK_AUTH_SERVER_URL"));
        config.put("KEYCLOAK_REALM", System.getenv("KEYCLOAK_REALM"));
        config.put("MESSAGE",
                "Verify that KEYCLOAK_REDIRECT_URI matches exactly what's registered in Keycloak Admin Console > Clients > teste-cli > Valid Redirect URIs");
        return ResponseEntity.ok(config);
    }
}
