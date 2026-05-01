package br.gov.pb.der.netnotify.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotify.dto.DirectNotifyRequest;
import br.gov.pb.der.netnotify.dto.RabbitAgentDto;
import br.gov.pb.der.netnotify.service.RabbitManagementService;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de gerenciamento de agentes conectados ao RabbitMQ.
 *
 * <p>
 * Todos os endpoints exigem autenticação com role ADMIN (veja
 * SecurityConfiguration).
 */
@RestController
@RequestMapping("/rabbit")
@RequiredArgsConstructor
public class RabbitManagementController {

    private final RabbitManagementService rabbitManagementService;
    private final ObjectMapper objectMapper;

    /**
     * Lista todos os agentes conectados ao RabbitMQ com seus IPs e filas.
     *
     * @param sortBy    campo de ordenação: {@code queue} (padrão) ou {@code ip}
     * @param direction sentido: {@code asc} (padrão) ou {@code desc}
     */
    @GetMapping("/agents")
    public ResponseEntity<List<RabbitAgentDto>> listAgents(
            @RequestParam(defaultValue = "queue") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        List<RabbitAgentDto> agents = rabbitManagementService.listConnectedAgents(sortBy, direction);
        return ResponseEntity.ok(agents);
    }

    /**
     * Envia uma mensagem isolada diretamente para a fila de um agente específico.
     *
     * <p>
     * O nome da fila deve ser exatamente o valor retornado no campo
     * {@code queueName}
     * do endpoint {@code GET /rabbit/agents}.
     *
     * @param queueName nome da fila de destino (path variable, URL-encoded)
     * @param request   dados da notificação
     */
    @PostMapping("/agents/{queueName}/notify")
    public ResponseEntity<Map<String, String>> notifyAgent(
            @PathVariable String queueName,
            @RequestBody DirectNotifyRequest request) {

        if (request == null || isBlank(request.getContent())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Conteúdo da mensagem é obrigatório."));
        }

        DirectNotifyRequest normalized = normalizeDirectNotifyRequest(request);

        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Falha ao serializar payload: " + e.getMessage()));
        }

        rabbitManagementService.sendDirectToQueue(queueName, json);

        return ResponseEntity.ok(Map.of(
                "status", "enviado",
                "queue", queueName));
    }

    private DirectNotifyRequest normalizeDirectNotifyRequest(DirectNotifyRequest request) {
        DirectNotifyRequest normalized = new DirectNotifyRequest();
        normalized.setTitle(trimToNull(request.getTitle()));
        normalized.setContent(request.getContent().trim());
        normalized.setLevel(defaultIfBlank(request.getLevel(), "Normal"));
        normalized.setType(defaultIfBlank(request.getType(), "Notificação"));
        return normalized;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : defaultValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
