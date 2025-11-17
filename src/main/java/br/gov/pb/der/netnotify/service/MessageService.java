package br.gov.pb.der.netnotify.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.MessageRepository;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService implements AbstractService<Message, UUID> {

    private final MessageRepository messageRepository;

    @Lazy
    private final RabbitmqService rabbitmqService;

    @Override
    public void save(Message entity) {
        messageRepository.save(entity);
    }

    @Override
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
                        row -> ((Number) row[1]).longValue()
                ));
    }

    public Map<String, Long> countTotalMessagesByType() {
        return messageRepository.countTotalMessagesByType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    public void sendScheduledMessages() {
        List<MessageResponseDto> messagesToSend = messageRepository.findMessagesForResend();
        for (MessageResponseDto messageDto : messagesToSend) {
            Message message = findById(messageDto.getId());
            sendNotification(message);
            messageRepository.updateLastSentAtById(message.getId(), java.time.LocalDateTime.now());
        }
    }

    public String sendNotification(Message message) {
        String msg = message.objectMapper().jsonStringfy();
        rabbitmqService.basicPublish(msg);
        return msg;
    }

}
