package br.gov.pb.der.netnotify.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RabbitAgentDto {

    private String queueName;

    /** "agente" para a fila única do agente; "legado-departamento" para filas antigas */
    private String queueType;

    /** Hostname do agente extraído do nome da fila */
    private String agentHostname;

    /** Nome do departamento, quando inferível a partir de convenções legadas */
    private String department;

    /** IP do consumidor (peer) */
    private String peerHost;

    /** Porta do consumidor */
    private int peerPort;

    /** IP:porta no formato "x.x.x.x:port" */
    private String peerAddress;

    /** Mensagens pendentes na fila */
    private int messageCount;

    /** Nome da conexão reportado pelo RabbitMQ */
    private String connectionName;

    /** Routing keys atualmente ligadas à fila do agente */
    private List<String> routingKeys;
}
