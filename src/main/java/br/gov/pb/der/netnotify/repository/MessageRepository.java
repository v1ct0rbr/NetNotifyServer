package br.gov.pb.der.netnotify.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.custom.MessageRepositoryCustom;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID>, MessageRepositoryCustom {

    // Listar mensagens paginado
    Page<Message> findAll(Pageable pageable);

}
