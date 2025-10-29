package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class RecoveryJwtTokenDto implements Serializable {

        private String acessToken;

        private String refreshToken;

        private Long expiresIn;

        private UserInfo user;

        public RecoveryJwtTokenDto(String acessToken, String refreshToken, Long expiresIn, UserInfo user) {
                this.acessToken = acessToken;
                this.refreshToken = refreshToken;
                this.expiresIn = expiresIn;
                this.user = user;
        }

        // Getters and Setters
}