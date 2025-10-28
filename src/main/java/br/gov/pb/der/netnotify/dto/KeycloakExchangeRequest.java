package br.gov.pb.der.netnotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("session_state")
    private String sessionState;     // Session state do Keycloak    
    private String redirect_uri;       // URI para redirecionar após exchange
}
