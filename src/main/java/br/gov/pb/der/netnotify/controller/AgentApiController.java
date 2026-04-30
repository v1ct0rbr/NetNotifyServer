package br.gov.pb.der.netnotify.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.AgentMessageDto;
import br.gov.pb.der.netnotify.repository.impl.MessageRepositoryImpl;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/agent-api")
@RequiredArgsConstructor
public class AgentApiController {

    private static final Logger logger = LoggerFactory.getLogger(AgentApiController.class);

    private final MessageRepositoryImpl messageRepositoryImpl;

    /**
     * Retorna mensagens ativas para o agente web.
     *
     * @param since          ISO datetime da última verificação (ex: 2025-01-01T00:00:00)
     * @param departmentName Nome do departamento do agente (opcional)
     * @param agentType      Tipo do agente: "internal" ou "external" (header X-Agent-Type)
     */
    @GetMapping("/messages")
    public ResponseEntity<List<AgentMessageDto>> getMessagesForAgent(
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String departmentName,
            @RequestHeader(value = "X-Agent-Type", defaultValue = "internal") String agentType) {

        logger.info("[AgentApi] Poll recebido - agentType={}, departmentName={}, since={}", agentType, departmentName, since);

        LocalDateTime sinceDateTime;
        if (since == null || since.isBlank()) {
            sinceDateTime = LocalDateTime.now().minusMinutes(5);
        } else {
            try {
                sinceDateTime = LocalDateTime.parse(since);
            } catch (DateTimeParseException e) {
                logger.warn("[AgentApi] Formato de 'since' inválido: {}. Usando 5 min atrás.", since);
                sinceDateTime = LocalDateTime.now().minusMinutes(5);
            }
        }

        List<AgentMessageDto> messages = messageRepositoryImpl.findMessagesForAgent(agentType, departmentName, sinceDateTime);
        logger.info("[AgentApi] Retornando {} mensagem(ens) para agentType={}", messages.size(), agentType);

        return ResponseEntity.ok(messages);
    }
}
