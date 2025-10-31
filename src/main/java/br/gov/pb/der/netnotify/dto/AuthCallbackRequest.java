package br.gov.pb.der.netnotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCallbackRequest {
    private String code;
    
    // The exact redirect_uri used in the authorization request (must match for token exchange)
    @JsonProperty("redirect_uri")
    private String redirect_uri;
    
    // Optional: PKCE code_verifier when authorization used PKCE (e.g., S256)
    @JsonProperty("code_verifier")
    private String code_verifier;

}
