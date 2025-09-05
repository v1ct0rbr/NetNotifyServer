package br.gov.pb.der.netnotify.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.model.MessageType;
import br.gov.pb.der.netnotify.repository.MessageTypeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageTypeService implements AbstractService<MessageType, Integer> {

    private final MessageTypeRepository messageTypeRepository;

    @Override
    public void save(MessageType messageType) {
        messageTypeRepository.save(messageType);
    }

    @Override
    public void delete(MessageType messageType) {
        messageTypeRepository.delete(messageType);
    }

    @Override
    @Cacheable(value="messageTypes", key="#id")
    public MessageType findById(Integer id) {
        return messageTypeRepository.findById(id).orElse(null);
    }

    @Override
    @Cacheable(value="messageTypes", key="'all_message_types'")
    public List<MessageType> findAll() {
        return messageTypeRepository.findAll();
    }
}
