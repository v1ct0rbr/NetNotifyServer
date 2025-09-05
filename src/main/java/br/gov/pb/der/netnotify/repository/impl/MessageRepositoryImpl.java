package br.gov.pb.der.netnotify.repository.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.repository.custom.MessageRepositoryCustom;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class MessageRepositoryImpl implements MessageRepositoryCustom {

    @PersistenceContext
    EntityManager entityManager;

    @Override
    public Page<MessageResponseDto> findAllMessages(MessageFilter filter, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("SELECT new br.gov.pb.der.netnotify.response.MessageResponseDto(m.id, "
                + "m.content, m.level.nome, m.type.name, m.user.username, m.createdAt, m.updatedAt) FROM Message m WHERE 1=1");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(m) FROM Message m WHERE 1=1");

        // Filtros dinâmicos
        if (filter != null) {
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                jpql.append(" AND LOWER(m.content) LIKE LOWER(:content)");
                countJpql.append(" AND LOWER(m.content) LIKE LOWER(:content)");
            }
            if (filter.getLevelId() != null) {
                jpql.append(" AND m.level.id = :levelId");
                countJpql.append(" AND m.level.id = :levelId");
            }
            if (filter.getMessageTypeId() != null) {
                jpql.append(" AND m.type.id = :messageTypeId");
                countJpql.append(" AND m.type.id = :messageTypeId");
            }
        }

        // Ordenação
        if (pageable.getSort().isSorted()) {
            jpql.append(" ORDER BY ");
            jpql.append(pageable.getSort().stream()
                    .map(order -> "m." + order.getProperty() + (order.isAscending() ? " ASC" : " DESC"))
                    .reduce((a, b) -> a + ", " + b).orElse("m.createdAt DESC"));
        } else {
            jpql.append(" ORDER BY m.createdAt DESC");
        }

        var query = entityManager.createQuery(jpql.toString(), MessageResponseDto.class);
        var countQuery = entityManager.createQuery(countJpql.toString(), Long.class);

        // Setar parâmetros
        if (filter != null) {
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                query.setParameter("content", "%" + filter.getContent() + "%");
                countQuery.setParameter("content", "%" + filter.getContent() + "%");
            }
            if (filter.getLevelId() != null) {
                query.setParameter("levelId", filter.getLevelId());
                countQuery.setParameter("levelId", filter.getLevelId());
            }
            if (filter.getMessageTypeId() != null) {
                query.setParameter("messageTypeId", filter.getMessageTypeId());
                countQuery.setParameter("messageTypeId", filter.getMessageTypeId());
            }
        }

        // Paginação
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        var resultList = query.getResultList();
        var total = countQuery.getSingleResult();

        return new org.springframework.data.domain.PageImpl<>(resultList, pageable, total);
    }
    // 
}
