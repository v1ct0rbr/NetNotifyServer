package br.gov.pb.der.netnotify.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;    
    
    private String content;
    private User user;
    private LocalDateTime timestamp;

    private Level level;
    private Tipo tipo;

    

}
