package br.gov.pb.der.netnotify.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class MessageType {

    public static final Integer T_NOTICIA = 1;
    public static final Integer T_NOTIFICACAO = 2;
    public static final Integer T_ALERTA = 3;

    @Id
    private Integer id;

    @Column(name = "tipo")
    private String name;

    public MessageType(Integer id) {
        this.id = id;
    }

}
