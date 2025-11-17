package br.gov.pb.der.netnotify.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.custom.MessageRepositoryCustom;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID>, MessageRepositoryCustom {

    @Query("SELECT COUNT(m) FROM Message m")
    public Long countTotal();

    public Long countByLevelId(Integer levelId);

    public Long countByTypeId(Integer typeId);

    @Query("SELECT l.name, COUNT(m) FROM Message m INNER JOIN m.level l GROUP BY l.name")
    public List<Object[]> countTotalMessagesByLevel();

    @Query("SELECT t.name, COUNT(m) FROM Message m INNER JOIN m.type t GROUP BY t.name")
    public List<Object[]> countTotalMessagesByType();

    @Query("UPDATE Message m SET m.lastSentAt = :lastSentAt WHERE m.id = :id")
    @Modifying    
    public void updateLastSentAtById(UUID id, java.time.LocalDateTime lastSentAt);

}
