package br.gov.pb.der.netnotify.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class RecoveryJwtTokenDto implements Serializable {

        private String token;
        private UserInfo user;

        public RecoveryJwtTokenDto(String token, UserInfo user) {
                this.token = token;
                this.user = user;
        }

        // Getters and Setters
}