package br.gov.pb.der.netnotify.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000" })
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

    @PostMapping("/callback")
    public ResponseEntity<KeycloakTokenResponse> callback(
            @RequestBody AuthCallbackRequest request) {
        try {
            log.info("🔄 Recebido callback com código: {}",
                    request.getCode() != null ? request.getCode().substring(0, 20) + "..." : "null");

            if (request.getCode() == null || request.getCode().isEmpty()) {
                log.error("❌ Código não fornecido");
                return ResponseEntity.badRequest().build();
            }

            // Troca código por token
            KeycloakTokenResponse response = authService.exchangeCodeForToken(request.getCode());

            log.info("✅ Callback processado com sucesso para usuário: {}",
                    response.getUser() != null ? response.getUser().getEmail() : "unknown");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erro ao processar callback:", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
}
