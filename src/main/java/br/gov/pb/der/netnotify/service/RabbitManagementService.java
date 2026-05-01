package br.gov.pb.der.netnotify.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import br.gov.pb.der.netnotify.dto.RabbitAgentDto;

/**
 * Consulta a Management HTTP API do RabbitMQ para listar agentes conectados e
 * publicar mensagens isoladas diretamente em filas individuais.
 *
 * <p>Requer que o usuário configurado em {@code spring.rabbitmq.management.username}
 * possua a tag {@code management} ou {@code administrator} no RabbitMQ.
 */
@Service
public class RabbitManagementService {

    private static final Logger log = LoggerFactory.getLogger(RabbitManagementService.class);

    private static final String PREFIX_AGENT = "queue_agent_";
    private static final String PREFIX_DEPT = "queue_department_";

    private final RabbitmqService rabbitmqService;

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    @Value("${spring.rabbitmq.management.port:15672}")
    private int managementPort;

    @Value("${spring.rabbitmq.management.username:guest}")
    private String managementUsername;

    @Value("${spring.rabbitmq.management.password:guest}")
    private String managementPassword;

    public RabbitManagementService(RabbitmqService rabbitmqService) {
        this.rabbitmqService = rabbitmqService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Lista todos os agentes (consumidores) conectados ao RabbitMQ.
     *
     * @param sortBy    campo de ordenação: {@code "queue"} ou {@code "ip"}
     * @param direction sentido: {@code "asc"} ou {@code "desc"}
     * @return lista de agentes com informações de fila, IP e mensagens pendentes
     */
    public List<RabbitAgentDto> listConnectedAgents(String sortBy, String direction) {
        String encodedVhost = encodeVhost(virtualHost);
        List<Map<String, Object>> consumers = fetchConsumers(encodedVhost);
        Map<String, Integer> queueMessages = fetchQueueMessageCounts(encodedVhost);

        List<RabbitAgentDto> agents = buildAgentList(consumers, queueMessages);

        return sort(agents, sortBy, direction);
    }

    /**
     * Envia uma mensagem JSON diretamente para uma fila pelo nome (exchange padrão).
     *
     * @param queueName nome exato da fila
     * @param jsonBody  conteúdo JSON a ser publicado
     */
    public void sendDirectToQueue(String queueName, String jsonBody) {
        rabbitmqService.publishDirectToQueue(queueName, jsonBody);
    }

    // -------------------------------------------------------------------------
    // Management API calls
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchConsumers(String encodedVhost) {
        String url = buildManagementUrl("/api/consumers/" + encodedVhost);
        try {
            List<Map<String, Object>> result = createWebClient()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
            return result != null ? result : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao consultar consumers RabbitMQ Management API [{}]: {} - {}",
                    url, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Erro inesperado ao consultar consumers RabbitMQ Management API: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> fetchQueueMessageCounts(String encodedVhost) {
        String url = buildManagementUrl("/api/queues/" + encodedVhost);
        try {
            List<Map<String, Object>> queues = createWebClient()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (queues == null) return Map.of();

            Map<String, Integer> counts = new HashMap<>();
            for (Map<String, Object> q : queues) {
                String name = (String) q.get("name");
                Object messages = q.getOrDefault("messages", 0);
                counts.put(name, ((Number) messages).intValue());
            }
            return counts;
        } catch (Exception e) {
            log.warn("Não foi possível obter contagem de mensagens das filas: {}", e.getMessage());
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<RabbitAgentDto> buildAgentList(
            List<Map<String, Object>> consumers,
            Map<String, Integer> queueMessages) {

        List<RabbitAgentDto> agents = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map<String, Object> consumer : consumers) {
            Map<String, Object> queueInfo = (Map<String, Object>) consumer.get("queue");
            Map<String, Object> channelDetails = (Map<String, Object>) consumer.get("channel_details");

            if (queueInfo == null || channelDetails == null) continue;

            String queueName = (String) queueInfo.get("name");
            if (queueName == null) continue;
            if (!queueName.startsWith(PREFIX_AGENT) && !queueName.startsWith(PREFIX_DEPT)) continue;

            String peerHost = (String) channelDetails.get("peer_address");
            Object peerPortObj = channelDetails.getOrDefault("peer_port", 0);
            int peerPort = ((Number) peerPortObj).intValue();

            // Deduplica por fila + IP (um agente pode ter múltiplos consumers na mesma fila)
            String dedupKey = queueName + "|" + peerHost + ":" + peerPort;
            if (!seen.add(dedupKey)) continue;

            String connectionName = (String) channelDetails.get("connection_name");

            RabbitAgentDto dto = parseQueueName(queueName);
            dto.setPeerHost(peerHost);
            dto.setPeerPort(peerPort);
            dto.setPeerAddress(peerHost + ":" + peerPort);
            dto.setConnectionName(connectionName);
            dto.setMessageCount(queueMessages.getOrDefault(queueName, 0));

            agents.add(dto);
        }

        return agents;
    }

    /**
     * Extrai tipo, hostname e departamento a partir do nome da fila.
     *
     * <p>Convenção do agente:
     * <ul>
     *   <li>{@code queue_agent_{hostname}} — fila de broadcast geral</li>
     *   <li>{@code queue_department_{dept}_{hostname}} — fila de departamento</li>
     * </ul>
     *
     * <p>Estratégia de parse para departamento: o hostname é gerado pelo agente apenas
     * com {@code [a-zA-Z0-9\-_]}, enquanto o dept é sempre lowercase.  Encontramos o
     * hostname como o maior sufixo sem letras maiúsculas (após o último separador entre
     * dept e hostname).  Como não há delimitador fixo, reportamos o sufixo após a última
     * ocorrência de {@code _} como candidato a hostname, e o restante como dept — isso
     * funciona para hostnames simples; nomes de host compostos terão o dept com underscores.
     */
    private RabbitAgentDto parseQueueName(String queueName) {
        RabbitAgentDto dto = new RabbitAgentDto();
        dto.setQueueName(queueName);

        if (queueName.startsWith(PREFIX_AGENT)) {
            dto.setQueueType("geral");
            dto.setAgentHostname(queueName.substring(PREFIX_AGENT.length()));
            dto.setDepartment(null);
        } else if (queueName.startsWith(PREFIX_DEPT)) {
            dto.setQueueType("departamento");
            String rest = queueName.substring(PREFIX_DEPT.length()); // {dept}_{hostname}
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore > 0) {
                dto.setDepartment(rest.substring(0, lastUnderscore));
                dto.setAgentHostname(rest.substring(lastUnderscore + 1));
            } else {
                dto.setDepartment(rest);
                dto.setAgentHostname(rest);
            }
        } else {
            dto.setQueueType("outro");
            dto.setAgentHostname(queueName);
        }

        return dto;
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    private List<RabbitAgentDto> sort(List<RabbitAgentDto> agents, String sortBy, String direction) {
        Comparator<RabbitAgentDto> comparator;

        if ("ip".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(RabbitAgentDto::getPeerHost,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            // default: queue
            comparator = Comparator.comparing(RabbitAgentDto::getQueueName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        agents.sort(comparator);
        return agents;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WebClient createWebClient() {
        return WebClient.builder()
                .defaultHeaders(h -> h.setBasicAuth(managementUsername, managementPassword))
                .build();
    }

    private String buildManagementUrl(String path) {
        return "http://" + rabbitHost + ":" + managementPort + path;
    }

    private String encodeVhost(String vhost) {
        if ("/".equals(vhost)) return "%2F";
        return URLEncoder.encode(vhost, StandardCharsets.UTF_8);
    }
}
