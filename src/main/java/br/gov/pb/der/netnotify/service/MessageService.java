package br.gov.pb.der.netnotify.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.MessageRepository;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService implements AbstractService<Message, UUID> {

    private final MessageRepository messageRepository;

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

    public Page<MessageResponseDto> findAllMessages(MessageFilter filter, Pageable pageable) {
        return messageRepository.findAllMessages(filter, pageable);
    }

    @Override
    public List<Message> findAll() {
        return messageRepository.findAll();
    }

}
