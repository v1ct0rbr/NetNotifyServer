package br.gov.pb.der.netnotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDto implements java.io.Serializable {

    private String id;
    @Size(max = 1000, min = 10, message = "O conteúdo deve ter entre 10 e 1000 caracteres")
    private String content;
    @Size(max = 5, min = 1, message = "O nível deve ter entre 1 e 5 caracteres")
    @JsonProperty("level_id")
    private Integer levelId;
    @JsonProperty("message_type_id")
    @Size(max = 5, min = 1, message = "O tipo de mensagem deve ter entre 1 e 5 caracteres")
    private Integer messageTypeId;

}
