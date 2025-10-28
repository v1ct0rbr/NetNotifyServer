package br.gov.pb.der.netnotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para receber o authorization code do Keycloak
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakExchangeRequest {
    private String code;              // Authorization code do Keycloak
    private String state;             // CSRF token
    private String sessionState;     // Session state do Keycloak
    private String redirectUri;       // URI para redirecionar após exchange
}
