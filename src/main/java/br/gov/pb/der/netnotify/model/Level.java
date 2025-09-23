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
@Table(name = "Level")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Level {
    public static final Integer L_BAIXO = 1;
    public static final Integer L_NORMAL = 2;
    public static final Integer L_ALTO = 3;
    public static final Integer L_URGENTE = 4;

    @Id
    public Integer id;

    @Column(name = "name")
    private String name;

    public Level(Integer id) {
        this.id = id;
    }

}
