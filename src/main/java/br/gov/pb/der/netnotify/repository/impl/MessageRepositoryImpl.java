package br.gov.pb.der.netnotify.repository.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.Level_;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.model.MessageType_;
import br.gov.pb.der.netnotify.model.Message_;
import br.gov.pb.der.netnotify.repository.custom.MessageRepositoryCustom;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import br.gov.pb.der.netnotify.utils.Functions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class MessageRepositoryImpl implements MessageRepositoryCustom {

    @PersistenceContext
    EntityManager entityManager;

    @Override
    public Page<MessageResponseDto> findAllMessages(MessageFilter filter, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<MessageResponseDto> cq = cb.createQuery(MessageResponseDto.class);
        Root<Message> message = cq.from(Message.class);
        // Use explicit LEFT joins for associations to avoid implicit inner joins
        Join<Message, br.gov.pb.der.netnotify.model.Level> levelJoin = message.join(Message_.level, JoinType.LEFT);
        Join<Message, br.gov.pb.der.netnotify.model.MessageType> typeJoin = message.join(Message_.type, JoinType.LEFT);
        Join<Message, br.gov.pb.der.netnotify.model.User> userJoin = message.join(Message_.user, JoinType.LEFT);

    cq.select(cb.construct(MessageResponseDto.class,
        message.get(Message_.id),
        message.get(Message_.content),
        levelJoin.get(Level_.name),
        typeJoin.get(MessageType_.name),
        userJoin.get("username"),
        message.get(Message_.createdAt),
        message.get(Message_.updatedAt)));
        // Filtros dinâmicos
        List<Predicate> predicates = new ArrayList<>();
        if (filter != null) {
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.content)), Functions.toLowerCaseForQuery(filter.getContent())));
            }
            if (filter.getLevelId() != null) {
                predicates.add(cb.equal(levelJoin.get(Level_.id), filter.getLevelId()));
            }
            if (filter.getMessageTypeId() != null) {
                predicates.add(cb.equal(typeJoin.get(MessageType_.id), filter.getMessageTypeId()));
            }
        }
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        // Ordenação
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                if (order.isAscending()) {
                    cq.orderBy(cb.asc(message.get(order.getProperty())));
                } else {
                    cq.orderBy(cb.desc(message.get(order.getProperty())));
                }
            });
        } else {
            cq.orderBy(cb.desc(message.get("createdAt")));
        }
        TypedQuery<MessageResponseDto> query = entityManager.createQuery(cq);
        

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<MessageResponseDto> content = query.getResultList();
        Page<MessageResponseDto> resultPage = new org.springframework.data.domain.PageImpl<>(content, pageable, countMessages(filter));
        return resultPage;

    }

    @Override
    public Long countMessages(MessageFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Message> message = cq.from(Message.class);
        cq.select(cb.count(message));
        // Filtros dinâmicos
        List<Predicate> predicates = new ArrayList<>();
        if (filter != null) {
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.content)), Functions.toLowerCaseForQuery(filter.getContent())));
            }
            if (filter.getLevelId() != null) {
                predicates.add(cb.equal(message.get(Message_.level).get(Level_.id), filter.getLevelId()));
            }
            if (filter.getMessageTypeId() != null) {
                predicates.add(cb.equal(message.get(Message_.type).get(MessageType_.id), filter.getMessageTypeId()));
            }
        }
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        return entityManager.createQuery(cq).getSingleResult();
    }
}
