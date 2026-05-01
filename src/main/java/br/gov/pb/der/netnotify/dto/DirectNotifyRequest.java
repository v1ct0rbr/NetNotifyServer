package br.gov.pb.der.netnotify.dto;

import lombok.Data;

@Data
public class DirectNotifyRequest {

    private String title;
    private String content;

    /**
     * Nome do nível (ex: "INFO", "WARNING", "CRITICAL").
     * Opcional — se não informado, o agente usa seu padrão.
     */
    private String level;

    /** Tipo da mensagem. Opcional. */
    private String type;
}
