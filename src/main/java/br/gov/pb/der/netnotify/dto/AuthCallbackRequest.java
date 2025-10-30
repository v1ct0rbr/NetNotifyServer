package br.gov.pb.der.netnotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCallbackRequest {
    private String code;
    // The exact redirect_uri used in the authorization request (must match for token exchange)
    private String redirectUri;

}
