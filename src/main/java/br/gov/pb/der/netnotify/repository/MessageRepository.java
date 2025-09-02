package br.gov.pb.der.netnotify.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.model.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

}
