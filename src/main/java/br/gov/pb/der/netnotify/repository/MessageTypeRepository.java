package br.gov.pb.der.netnotify.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.gov.pb.der.netnotify.model.MessageType;

public interface MessageTypeRepository extends JpaRepository<MessageType, Integer> {
}
