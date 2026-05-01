package br.gov.pb.der.netnotify.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.config.CacheConfig;
import br.gov.pb.der.netnotify.dto.AgentMessageDto;
import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.AgentScope;
import br.gov.pb.der.netnotify.model.Department;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.MessageRepository;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService implements AbstractService<Message, UUID> {

    private final MessageRepository messageRepository;

    private final DepartmentService departmentService;

    @Lazy
    private final RabbitmqService rabbitmqService;

    @Value("${app.default-office-hours-windows}")
    private String defaultOfficeHoursWindow;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "agentMessages", allEntries = true)
    })
    public void save(Message entity) {
        messageRepository.save(entity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "agentMessages", allEntries = true)
    })
    public void delete(Message entity) {
        messageRepository.delete(entity);
    }

    @Override
    public Message findById(UUID id) {
        return messageRepository.findById(id).orElse(null);
    }

    public Message findById(String id) {
        return messageRepository.findById(UUID.fromString(id)).orElse(null);
    }

    public MessageDto findMessageDtoById(UUID id) {
        return messageRepository.findMessageDtoById(id);
    }

    public Page<MessageResponseDto> findAllMessages(MessageFilter filter, Pageable pageable) {
        return messageRepository.findAllMessages(filter, pageable);
    }

    @Override
    public List<Message> findAll() {
        return messageRepository.findAll();
    }

    public Long countTotalMessages() {
        return messageRepository.countTotal();
    }

    public Map<String, Long> countTotalMessagesByLevel() {
        return messageRepository.countTotalMessagesByLevel().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));
    }

    public Map<String, Long> countTotalMessagesByType() {
        return messageRepository.countTotalMessagesByType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));
    }

    public void sendScheduledMessages() {
        List<MessageResponseDto> messagesToSend = messageRepository.findMessagesForResend();
        for (MessageResponseDto messageDto : messagesToSend) {
            Message message = findById(messageDto.getId());
            sendNotification(message);

        }
    }

    /**
     * Retorna mensagens ativas para um agente web, com base no tipo
     * (internal/external),
     * departamento e janela de tempo {@code since}.
     * <p>
     * Não é cacheado: {@code since} varia em cada request e uma chave de cache sem
     * ele
     * produziria resultados incorretos (agentes com janelas distintas receberiam os
     * dados
     * do primeiro que fez o cache). A latência do Dragonfly é baixa o suficiente
     * para
     * queries simples sem camada adicional de cache.
     */
    public List<AgentMessageDto> findMessagesForAgent(String agentType, String departmentName,
            java.time.LocalDateTime since) {
        return messageRepository.findMessagesForAgent(agentType, departmentName, since);
    }

    @CacheEvict(value = "agentMessages", allEntries = true)
    public String sendNotification(Message message) {
        String msg = message.objectMapper().jsonStringfy();

        // Agentes que usam mensageria (RabbitMQ) são sempre tratados como agentes
        // internos.
        // Só publicar no broker se o escopo da mensagem inclui agentes internos
        // (INTERNAL ou BOTH).
        AgentScope scope = message.getAgentScope();
        boolean publishToMessaging = scope == null || scope == AgentScope.INTERNAL || scope == AgentScope.BOTH;

        if (publishToMessaging) {
            List<Department> departmentList = message.getDepartments();
            if (departmentList.isEmpty()) {
                // Broadcast geral: somente para quem escuta broadcast.*
                // System.out.println("[Routing] Broadcast geral messageId=" + message.getId());
                rabbitmqService.basicPublish(msg);
            } else {
                // Segmentado por departamentos (e opcionalmente subdivisões)
                // System.out.println("[Routing] Segmentado messageId=" + message.getId() + "
                // departamentos=" +
                // departmentList.stream().map(Department::getName).collect(Collectors.joining(","))
                // +
                // " sendToSubdivisions=" + message.getSendToSubdivisions());

                if (Boolean.TRUE.equals(message.getSendToSubdivisions())) {
                    for (Department dept : departmentList) {
                        List<Department> subdivisions = departmentService.findByParentDepartmentId(dept.getId());

                        for (Department subDept : subdivisions) {
                            if (departmentList.contains(subDept)) {
                                continue; // Já está na lista principal
                            }
                            String subDeptName = subDept.getName().toLowerCase().replace(" ", "_");
                            rabbitmqService.publishToDepartment(msg, subDeptName);
                        }
                    }
                }
                rabbitmqService.publishToDepartments(msg, departmentList.stream()
                        .map(dept -> dept.getName().toLowerCase().replace(" ", "_"))
                        .collect(Collectors.toList()));
            }
        } else {
            System.out.println("[Routing] Mensagem ignorada no broker (agentScope=" + scope +
                    ", agentes de mensageria são internos) messageId=" + message.getId());
        }

        messageRepository.updateLastSentAtById(message.getId(), java.time.LocalDateTime.now());

        return msg;
    }

    public Integer updateLastSentAtById(UUID id, java.time.LocalDateTime lastSentAt) {
        return messageRepository.updateLastSentAtById(id, lastSentAt);
    }

    @Cacheable(value = CacheConfig.DEFAULT_OFFICE_HOURS_WINDOW, key = "'default_office_hours_window'")
    public String getDefaultOfficeHoursWindow() {
        return defaultOfficeHoursWindow;
    }

}
