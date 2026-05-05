package br.gov.pb.der.netnotify.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
 * enderecar notificações isoladas para agentes individuais.
 *
 * <p>
 * Requer que o usuário configurado em
 * {@code spring.rabbitmq.management.username}
 * possua a tag {@code management} ou {@code administrator} no RabbitMQ.
 */
@Service
public class RabbitManagementService {

    private static final Logger log = LoggerFactory.getLogger(RabbitManagementService.class);

    private static final String PREFIX_AGENT = "queue_agent_";
    private static final String PREFIX_DEPT = "queue_department_";

    private final br.gov.pb.der.netnotify.service.RabbitmqService rabbitmqService;

    @Value("${spring.rabbitmq.management.host:${spring.rabbitmq.host:localhost}}")
    private String managementHost;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    @Value("${spring.rabbitmq.management.port:15672}")
    private int managementPort;

    @Value("${spring.rabbitmq.management.username:guest}")
    private String managementUsername;

    @Value("${spring.rabbitmq.management.password:guest}")
    private String managementPassword;

    @Value("${spring.rabbitmq.exchange:${RABBITMQ_EXCHANGE:netnotify_topic}}")
    private String exchangeName;

    public RabbitManagementService(br.gov.pb.der.netnotify.service.RabbitmqService rabbitmqService) {
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
        String targetVhost = normalizeVhost(virtualHost);
        List<Map<String, Object>> consumers = fetchConsumers();
        Map<String, QueueStats> queueStats = fetchQueueStats(targetVhost);
        Map<String, List<String>> queueBindings = fetchQueueBindings(targetVhost);

        List<RabbitAgentDto> agents = buildAgentList(consumers, queueStats, queueBindings, targetVhost);
        if (agents.isEmpty()) {
            // Em algumas permissões/tags do RabbitMQ Management API, /api/consumers pode
            // retornar vazio mesmo com filas ativas. Nesse caso, fazemos fallback por fila.
            agents = buildAgentListFromQueues(queueStats, queueBindings);
        }

        return sort(agents, sortBy, direction);
    }

    /**
     * Envia uma mensagem JSON diretamente para uma fila pelo nome (exchange
     * padrão).
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

    private List<Map<String, Object>> fetchConsumers() {
        String url = buildManagementUrl("/api/consumers");
        try {
            List<Map<String, Object>> result = createWebClient()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    })
                    .block();
            return result != null ? result : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao consultar consumers RabbitMQ Management API [{}]: {} - {}",
                    url, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error(
                    "Erro inesperado ao consultar consumers RabbitMQ Management API [{}] (host={}, port={}): {}",
                    url,
                    managementHost,
                    managementPort,
                    e.getMessage());
            return List.of();
        }
    }

    private Map<String, QueueStats> fetchQueueStats(String targetVhost) {
        String url = buildManagementUrl("/api/queues");
        try {
            List<Map<String, Object>> queues = createWebClient()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    })
                    .block();

            if (queues == null)
                return Map.of();

            Map<String, QueueStats> stats = new HashMap<>();
            for (Map<String, Object> q : queues) {
                String name = (String) q.get("name");
                String queueVhost = normalizeVhost((String) q.get("vhost"));
                if (!isSameVhost(targetVhost, queueVhost))
                    continue;
                if (name == null)
                    continue;

                Object messagesObj = q.getOrDefault("messages", 0);
                Object consumersObj = q.getOrDefault("consumers", 0);
                int messages = messagesObj instanceof Number ? ((Number) messagesObj).intValue() : 0;
                int consumers = consumersObj instanceof Number ? ((Number) consumersObj).intValue() : 0;

                stats.put(name, new QueueStats(messages, consumers));
            }
            return stats;
        } catch (Exception e) {
            log.warn(
                    "Não foi possível obter contagem de mensagens das filas via RabbitMQ Management API [{}] (host={}, port={}): {}",
                    url,
                    managementHost,
                    managementPort,
                    e.getMessage());
            return Map.of();
        }
    }

    private Map<String, List<String>> fetchQueueBindings(String targetVhost) {
        String encodedVhost = encodePathSegment(targetVhost);
        String url = buildManagementUrl("/api/bindings/" + encodedVhost);
        try {
            List<Map<String, Object>> bindings = createWebClient()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    })
                    .block();

            if (bindings == null) {
                return Map.of();
            }

            Map<String, List<String>> result = new HashMap<>();
            for (Map<String, Object> binding : bindings) {
                String bindingVhost = normalizeVhost(asString(binding.get("vhost")));
                if (!isSameVhost(targetVhost, bindingVhost)) {
                    continue;
                }

                String destinationType = asString(binding.get("destination_type"));
                String source = asString(binding.get("source"));
                String destination = asString(binding.get("destination"));
                String routingKey = asString(binding.get("routing_key"));

                if (!"queue".equalsIgnoreCase(destinationType)) {
                    continue;
                }
                if (!exchangeName.equals(source)) {
                    continue;
                }
                if (isBlank(destination) || isBlank(routingKey)) {
                    continue;
                }

                result.computeIfAbsent(destination, ignored -> new ArrayList<>());
                List<String> routingKeys = result.get(destination);
                if (!routingKeys.contains(routingKey)) {
                    routingKeys.add(routingKey);
                }
            }

            return result;
        } catch (Exception e) {
            log.warn(
                    "Não foi possível obter bindings das filas via RabbitMQ Management API [{}] (host={}, port={}): {}",
                    url,
                    managementHost,
                    managementPort,
                    e.getMessage());
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<RabbitAgentDto> buildAgentList(
            List<Map<String, Object>> consumers,
            Map<String, QueueStats> queueStats,
            Map<String, List<String>> queueBindings,
            String targetVhost) {

        List<RabbitAgentDto> agents = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map<String, Object> consumer : consumers) {
            Map<String, Object> queueInfo = (Map<String, Object>) consumer.get("queue");
            Map<String, Object> channelDetails = (Map<String, Object>) consumer.get("channel_details");

            if (queueInfo == null || channelDetails == null)
                continue;

            String queueName = (String) queueInfo.get("name");
            String queueVhost = normalizeVhost((String) queueInfo.get("vhost"));
            if (queueName == null)
                continue;
            if (!isSameVhost(targetVhost, queueVhost))
                continue;
            if (!queueName.startsWith(PREFIX_AGENT))
                continue;

            String rawPeerAddress = asString(channelDetails.get("peer_address"));
            String peerHost = extractPeerHost(rawPeerAddress);
            if (isBlank(peerHost)) {
                peerHost = asString(channelDetails.get("peer_host"));
            }
            Object peerPortObj = channelDetails.getOrDefault("peer_port", 0);
            int peerPort = numberToInt(peerPortObj, extractPeerPort(rawPeerAddress));

            String resolvedPeerHost = bestEffortResolveIp(peerHost);
            if (isBlank(resolvedPeerHost)) {
                resolvedPeerHost = "N/D";
            }

            String dedupKey = queueName;
            if (!seen.add(dedupKey))
                continue;

            String connectionName = (String) channelDetails.get("connection_name");

            RabbitAgentDto dto = parseQueueName(queueName, queueBindings.getOrDefault(queueName, List.of()));
            dto.setPeerHost(resolvedPeerHost);
            dto.setPeerPort(peerPort);
            dto.setPeerAddress(resolvedPeerHost);
            dto.setConnectionName(connectionName);
            dto.setMessageCount(queueStats.getOrDefault(queueName, QueueStats.ZERO).messageCount());

            agents.add(dto);
        }

        return agents;
    }

    private List<RabbitAgentDto> buildAgentListFromQueues(
            Map<String, QueueStats> queueStats,
            Map<String, List<String>> queueBindings) {
        List<RabbitAgentDto> agents = new ArrayList<>();

        for (Map.Entry<String, QueueStats> entry : queueStats.entrySet()) {
            String queueName = entry.getKey();
            QueueStats stats = entry.getValue();

            if (queueName == null)
                continue;
            if (!queueName.startsWith(PREFIX_AGENT))
                continue;
            if (stats.consumerCount() <= 0)
                continue;

            RabbitAgentDto dto = parseQueueName(queueName, queueBindings.getOrDefault(queueName, List.of()));
            String resolvedPeerHost = bestEffortResolveIp(dto.getAgentHostname());
            if (isBlank(resolvedPeerHost)) {
                resolvedPeerHost = dto.getAgentHostname();
            }
            if (isBlank(resolvedPeerHost)) {
                resolvedPeerHost = "N/D";
            }

            dto.setPeerHost(resolvedPeerHost);
            dto.setPeerPort(0);
            dto.setPeerAddress(resolvedPeerHost);
            dto.setConnectionName(null);
            dto.setMessageCount(stats.messageCount());
            agents.add(dto);
        }

        if (!agents.isEmpty()) {
            log.info("Fallback de agentes por filas aplicado: {} agente(s) detectado(s).", agents.size());
        }

        return agents;
    }

    /**
     * Extrai tipo, hostname e departamento a partir do nome da fila.
     *
     * <p>
     * Convenção atual do agente:
     * <ul>
     * <li>{@code queue_agent_{hostname}} — fila única com bindings de broadcast,
     * departamento e agente específico</li>
     * </ul>
     *
     * <p>
     * Estratégia de parse para departamento: o hostname é gerado pelo agente apenas
     * com {@code [a-zA-Z0-9\-_]}, enquanto o dept é sempre lowercase. Encontramos o
     * hostname como o maior sufixo sem letras maiúsculas (após o último separador
     * entre
     * dept e hostname). Como não há delimitador fixo, reportamos o sufixo após a
     * última
     * ocorrência de {@code _} como candidato a hostname, e o restante como dept —
     * isso
     * funciona para hostnames simples; nomes de host compostos terão o dept com
     * underscores.
     */
    private RabbitAgentDto parseQueueName(String queueName, List<String> routingKeys) {
        RabbitAgentDto dto = new RabbitAgentDto();
        dto.setQueueName(queueName);
        dto.setRoutingKeys(routingKeys);

        if (queueName.startsWith(PREFIX_AGENT)) {
            dto.setQueueType("agente");
            dto.setAgentHostname(queueName.substring(PREFIX_AGENT.length()));
            dto.setDepartment(extractDepartment(routingKeys));
        } else if (queueName.startsWith(PREFIX_DEPT)) {
            dto.setQueueType("legado-departamento");
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
        return "http://" + managementHost + ":" + managementPort + path;
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalizeVhost(String vhost) {
        return (vhost == null || vhost.isBlank()) ? "/" : vhost;
    }

    private boolean isSameVhost(String left, String right) {
        return normalizeVhost(left).equals(normalizeVhost(right));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String extractDepartment(List<String> routingKeys) {
        if (routingKeys == null || routingKeys.isEmpty()) {
            return null;
        }

        List<String> departments = new ArrayList<>();
        for (String routingKey : routingKeys) {
            if (routingKey == null || !routingKey.startsWith("department.")) {
                continue;
            }
            String normalizedDepartment = routingKey.substring("department.".length());
            if (normalizedDepartment.isBlank()) {
                continue;
            }
            String displayName = normalizedDepartment.replace('_', ' ');
            if (!departments.contains(displayName)) {
                departments.add(displayName);
            }
        }

        return departments.isEmpty() ? null : String.join(", ", departments);
    }

    private int numberToInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String extractPeerHost(String rawPeerAddress) {
        if (isBlank(rawPeerAddress)) {
            return null;
        }

        String peerAddress = rawPeerAddress.trim();

        // IPv6 entre colchetes: [fe80::1]:1234
        if (peerAddress.startsWith("[")) {
            int closeIdx = peerAddress.indexOf(']');
            if (closeIdx > 1) {
                return peerAddress.substring(1, closeIdx);
            }
            return peerAddress;
        }

        int firstColon = peerAddress.indexOf(':');
        int lastColon = peerAddress.lastIndexOf(':');

        // Hostname/IPv4 com porta: 127.0.0.1:54321
        if (firstColon > 0 && firstColon == lastColon) {
            return peerAddress.substring(0, firstColon);
        }

        // IPv6 sem colchetes (sem porta explícita)
        return peerAddress;
    }

    private int extractPeerPort(String rawPeerAddress) {
        if (isBlank(rawPeerAddress)) {
            return 0;
        }

        String peerAddress = rawPeerAddress.trim();

        if (peerAddress.startsWith("[")) {
            int closeIdx = peerAddress.indexOf(']');
            if (closeIdx > 0 && closeIdx + 2 <= peerAddress.length() && peerAddress.charAt(closeIdx + 1) == ':') {
                return safeParseInt(peerAddress.substring(closeIdx + 2), 0);
            }
            return 0;
        }

        int firstColon = peerAddress.indexOf(':');
        int lastColon = peerAddress.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon && lastColon + 1 < peerAddress.length()) {
            return safeParseInt(peerAddress.substring(lastColon + 1), 0);
        }

        return 0;
    }

    private int safeParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String bestEffortResolveIp(String hostOrIp) {
        if (isBlank(hostOrIp)) {
            return null;
        }

        String value = hostOrIp.trim();
        // Já parece IPv4
        if (value.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) {
            return value;
        }

        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException e) {
            return value;
        }
    }

    private record QueueStats(int messageCount, int consumerCount) {
        private static final QueueStats ZERO = new QueueStats(0, 0);
    }
}
