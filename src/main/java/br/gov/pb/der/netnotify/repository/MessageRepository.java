package br.gov.pb.der.netnotify.repository;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT m.level.name, COUNT(m) FROM Message m GROUP BY m.level.name")
    public Map<String, Long> countTotalMessagesByLevel();

    @Query("SELECT m.type.name, COUNT(m) FROM Message m GROUP BY m.type.name")
    public Map<String, Long> countTotalMessagesByType();

}
