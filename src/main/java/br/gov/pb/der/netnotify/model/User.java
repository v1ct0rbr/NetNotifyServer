package br.gov.pb.der.netnotify.model;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Representa um usuário de domínio Windows / Active Directory com os
 * atributos mais comuns usados em autenticação/autorização.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "users", schema = "auth")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "UUID")
    private UUID id;

    // sAMAccountName - nome de login no AD (ex: jdoe)
    private String username;

    // userPrincipalName - normalmente user@domain (ex: jdoe@example.org)
    private String userPrincipalName;

    // Nome exibido
    private String displayName;

    // Given name (primeiro nome)
    private String givenName;

    // Surname / sobrenome
    private String sn;

    // Email
    private String mail;

    // Domínio/realm (ex: EXAMPLE)
    private String domain;

    // Usuário habilitado no AD
    private boolean enabled;

    // Grupos/roles do usuário (memberOf) - lista de DNs ou nomes de grupo
    // simplificados
    private List<String> groups;

}
