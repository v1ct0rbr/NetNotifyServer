package br.gov.pb.der.netnotify.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.MessageRepository;
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

    @Override
    public List<Message> findAll() {
        return messageRepository.findAll();
    }

}
