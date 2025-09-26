package br.gov.pb.der.netnotify.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.custom.MessageRepositoryCustom;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID>, MessageRepositoryCustom {

    @Query("SELECT new br.gov.pb.der.netnotify.dto.MessageDto(m.content, l.id, t.id) "
            + "FROM Message m "
            + "LEFT JOIN m.level l "
            + "LEFT JOIN m.type t "
            + "WHERE m.id = :id")
    public MessageDto findMessageDtoById(UUID id);

}
