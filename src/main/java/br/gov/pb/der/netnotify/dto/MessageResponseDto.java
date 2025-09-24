package br.gov.pb.der.netnotify.dto;

import lombok.Data;

@Data
public class MessageResponseDto implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String content;
    private String sender;
    private String level;
    private String type;
    private String createdAt;
    private String updatedAt;
}
