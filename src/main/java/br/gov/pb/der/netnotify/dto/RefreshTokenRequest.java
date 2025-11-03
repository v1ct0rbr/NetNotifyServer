package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class RefreshTokenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String refreshToken;

}
