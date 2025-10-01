package br.gov.pb.der.netnotify.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDto implements java.io.Serializable {

    private String id;
    @Size(max = 10000, min = 10, message = "O conteúdo deve ter entre 10 e 10000 caracteres")

    private String title;

    private String content;
    @Min(value = 1, message = "O nível deve ser maior que 0")
    @Max(value = 5, message = "O nível deve ser menor que 6")

    private Integer level;
    @Max(value = 5, message = "O tipo de mensagem deve ser menor que 6")
    @Min(value = 1, message = "O tipo de mensagem deve ser maior que 0")

    private Integer type;

    public MessageDto() {
    }

    public MessageDto(String content, Integer level, Integer type) {
        this.content = content;
        this.level = level;
        this.type = type;
    }

}
