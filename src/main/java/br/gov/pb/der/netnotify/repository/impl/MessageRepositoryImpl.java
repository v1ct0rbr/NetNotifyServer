package br.gov.pb.der.netnotify.repository.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import br.gov.pb.der.netnotify.dto.AgentMessageDto;
import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.AgentScope;
import br.gov.pb.der.netnotify.model.Level_;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.model.MessageType_;
import br.gov.pb.der.netnotify.model.Message_;
import br.gov.pb.der.netnotify.model.User_;
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

    @Value("${app.default-office-hours-windows:[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"2\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"3\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"4\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"5\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]}")
    private String defaultOfficeHoursWindowsJson;

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
                message.get(Message_.title),
                cb.substring(message.get(Message_.content), 1, 20),
                levelJoin.get(Level_.name),
                typeJoin.get(MessageType_.name),
                userJoin.get("username"),
                message.get(Message_.createdAt),
                message.get(Message_.updatedAt),
                message.get(Message_.expireAt),
                message.get(Message_.lastSentAt),
                message.get(Message_.repeatIntervalMinutes),
                message.get(Message_.sendToSubdivisions),
                message.get(Message_.publishedAt),
                message.get(Message_.scheduleDaysOfWeek),
                message.get(Message_.scheduleTimes),
                message.get(Message_.scheduleMonthDays),
                message.get(Message_.availabilityWindows)));
        // Filtros dinâmicos
        List<Predicate> predicates = new ArrayList<>();
        if (filter != null) {
            if (filter.getTitle() != null && !filter.getTitle().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.title)),
                        Functions.toLowerCaseForQuery(filter.getTitle())));
            }
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.content)),
                        Functions.toLowerCaseForQuery(filter.getContent())));
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
        List<MessageResponseDto> result = query.getResultList();

        // Enriquecer com departamentos vinculados à mensagem, evitando N+1
        if (!result.isEmpty()) {
            List<UUID> ids = result.stream().map(MessageResponseDto::getId).toList();
            // JPQL simples para buscar departamentos por mensagem
            List<Object[]> rows = entityManager.createQuery(
                    "select m.id, d.id, d.name from Message m left join m.departments d where m.id in :ids",
                    Object[].class)
                    .setParameter("ids", ids)
                    .getResultList();

            Map<UUID, List<MessageResponseDto.DepartmentInfo>> byMessage = new HashMap<>();
            for (Object[] r : rows) {
                UUID mid = (UUID) r[0];
                UUID did = (UUID) r[1];
                String dname = (String) r[2];
                byMessage.computeIfAbsent(mid, k -> new ArrayList<>())
                        .add(new MessageResponseDto.DepartmentInfo(did, dname));
            }
            for (MessageResponseDto dto : result) {
                List<MessageResponseDto.DepartmentInfo> deps = byMessage.get(dto.getId());
                if (deps != null) {
                    dto.setDepartments(deps);
                } else {
                    dto.setDepartments(java.util.Collections.emptyList());
                }
            }
        }
        // para cada objeto o atributo content deve ser completado com "..." se tiver
        // mais de 20 caracteres
        for (MessageResponseDto dto : result) {
            if (dto.getContent() != null) {
                if (dto.getContent().length() < 11) {
                    dto.setContent(Functions.removeHtmlTags(dto.getContent()) + "...");
                } else {
                    dto.setContent(Functions.sanitizeAndTruncate(dto.getContent(), 20) + "...");
                }
            }
        }

        Page<MessageResponseDto> resultPage = new org.springframework.data.domain.PageImpl<>(result, pageable,
                countMessages(filter));
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
            if (filter.getTitle() != null && !filter.getTitle().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.title)),
                        Functions.toLowerCaseForQuery(filter.getTitle())));
            }
            if (filter.getContent() != null && !filter.getContent().isEmpty()) {
                predicates.add(cb.like(cb.lower(message.get(Message_.content)),
                        Functions.toLowerCaseForQuery(filter.getContent())));
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

    @Override
    public List<MessageResponseDto> findMessagesForResend() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<MessageResponseDto> cq = cb.createQuery(MessageResponseDto.class);
        Root<Message> message = cq.from(Message.class);
        cq.select(cb.construct(MessageResponseDto.class,
                message.get(Message_.id),
                message.get(Message_.title),
                message.get(Message_.content),
                message.get(Message_.level).get(Level_.name),
                message.get(Message_.type).get(MessageType_.name),
                message.get(Message_.user).get(User_.username),
                message.get(Message_.createdAt),
                message.get(Message_.updatedAt),
                message.get(Message_.expireAt),
                message.get(Message_.lastSentAt),
                message.get(Message_.repeatIntervalMinutes),
                message.get(Message_.sendToSubdivisions),
                message.get(Message_.publishedAt),
                message.get(Message_.scheduleDaysOfWeek),
                message.get(Message_.scheduleTimes),
                message.get(Message_.scheduleMonthDays),
                message.get(Message_.availabilityWindows)));
        // Filtros dinâmicos

        List<Predicate> predicates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Apenas mensagens não expiradas
        Predicate notExpired = cb.or(
                cb.isNull(message.get(Message_.expireAt)),
                cb.greaterThan(message.get(Message_.expireAt), now));
        predicates.add(notExpired);

        // Candidatos:
        // A) Intervalo de repetição
        // B) Envio único (publishedAt vencido, nunca enviado, sem recorrência)
        // C) Recorrência semanal
        // D) Recorrência mensal
        Predicate isRecurring = cb.and(
                cb.isNotNull(message.get(Message_.repeatIntervalMinutes)),
                cb.greaterThan(message.get(Message_.repeatIntervalMinutes), 0));
        Predicate isFutureOneShotDue = cb.and(
                cb.isNull(message.get(Message_.repeatIntervalMinutes)),
                cb.isNull(message.get(Message_.scheduleDaysOfWeek)),
                cb.isNull(message.get(Message_.scheduleMonthDays)),
                cb.isNull(message.get(Message_.lastSentAt)),
                cb.isNotNull(message.get(Message_.publishedAt)),
                cb.lessThanOrEqualTo(message.get(Message_.publishedAt), now));
        Predicate isWeeklyScheduled = cb.isNotNull(message.get(Message_.scheduleDaysOfWeek));
        Predicate isMonthlyScheduled = cb.isNotNull(message.get(Message_.scheduleMonthDays));
        predicates.add(cb.or(isRecurring, isFutureOneShotDue, isWeeklyScheduled, isMonthlyScheduled));

        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        TypedQuery<MessageResponseDto> query = entityManager.createQuery(cq);
        List<MessageResponseDto> result = query.getResultList();
        List<MessageResponseDto> filteredResult = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        for (MessageResponseDto dto : result) {
            if (dto.getPublishedAt() != null && dto.getPublishedAt().isAfter(now)) {
                continue;
            }

            // --- Disponibilidade: verifica janelas de horário antes de qualquer disparo
            // ---
            if (!isWithinAvailabilityWindows(dto.getAvailabilityWindows())) {
                continue;
            }

            // --- Recorrência semanal ---
            if (dto.getScheduleDaysOfWeek() != null && !dto.getScheduleDaysOfWeek().isBlank()) {
                int todayIso = today.getDayOfWeek().getValue();
                boolean isTodayScheduled = Arrays.stream(dto.getScheduleDaysOfWeek().split(","))
                        .map(String::trim)
                        .anyMatch(d -> d.equals(String.valueOf(todayIso)));
                if (!isTodayScheduled)
                    continue;
                String scheduleTimesForToday = resolveScheduleTimesForDay(dto.getScheduleTimes(),
                        String.valueOf(todayIso));
                if (!isTimeSlotReached(scheduleTimesForToday, dto.getLastSentAt(), today, nowTime))
                    continue;
                feedDeparmentsToMessageResponseDto(dto);
                filteredResult.add(dto);
                continue;
            }

            // --- Recorrência mensal ---
            if (dto.getScheduleMonthDays() != null && !dto.getScheduleMonthDays().isBlank()) {
                int todayDom = today.getDayOfMonth();
                boolean isTodayScheduled = Arrays.stream(dto.getScheduleMonthDays().split(","))
                        .map(String::trim)
                        .anyMatch(d -> d.equals(String.valueOf(todayDom)));
                if (!isTodayScheduled)
                    continue;
                String scheduleTimesForToday = resolveScheduleTimesForDay(dto.getScheduleTimes(),
                        String.valueOf(todayDom));
                if (!isTimeSlotReached(scheduleTimesForToday, dto.getLastSentAt(), today, nowTime))
                    continue;
                feedDeparmentsToMessageResponseDto(dto);
                filteredResult.add(dto);
                continue;
            }

            // --- Intervalo de repetição ou envio único ---
            if (dto.getLastSentAt() != null && dto.getRepeatIntervalMinutes() != null) {
                LocalDateTime nextSendTime = dto.getLastSentAt().plusMinutes(dto.getRepeatIntervalMinutes());
                if (nextSendTime.isBefore(now) || nextSendTime.isEqual(now)) {
                    feedDeparmentsToMessageResponseDto(dto);
                    filteredResult.add(dto);
                }
            } else if (dto.getLastSentAt() == null) {
                feedDeparmentsToMessageResponseDto(dto);
                filteredResult.add(dto);
            }
        }
        return filteredResult;
    }

    public void feedDeparmentsToMessageResponseDto(MessageResponseDto dto) {
        if (dto == null || dto.getId() == null) {
            return;
        }
        List<Object[]> rows = entityManager.createQuery(
                "select d.id, d.name from Message m join m.departments d where m.id = :id",
                Object[].class)
                .setParameter("id", dto.getId())
                .getResultList();
        List<MessageResponseDto.DepartmentInfo> departments = new ArrayList<>();
        for (Object[] r : rows) {
            UUID did = (UUID) r[0];
            String dname = (String) r[1];
            departments.add(new MessageResponseDto.DepartmentInfo(did, dname));
        }
        dto.setDepartments(departments);
    }

    @Override
    public MessageDto findMessageDtoById(UUID id) {
        Message message = entityManager.find(Message.class, id);
        if (message == null) {
            return null;
        }
        MessageDto dto = new MessageDto(
                message.getTitle(),
                message.getContent(),
                message.getLevel() != null ? message.getLevel().getId() : null,
                message.getType() != null ? message.getType().getId() : null,
                message.getDepartments() != null
                        ? message.getDepartments().stream().map(d -> d.getId()).toList()
                        : new ArrayList<>(),
                message.getSendToSubdivisions(),
                message.getExpireAt(),
                message.getPublishedAt(),
                message.getRepeatIntervalMinutes());
        dto.setScheduleDaysOfWeek(message.getScheduleDaysOfWeek());
        dto.setScheduleTimes(message.getScheduleTimes());
        dto.setScheduleMonthDays(message.getScheduleMonthDays());
        dto.setAvailabilityWindows(message.getAvailabilityWindows());
        return dto;

    }

    @Override
    public List<AgentMessageDto> findMessagesForAgent(String agentType, String departmentName, LocalDateTime since) {
        LocalDateTime now = LocalDateTime.now();

        List<AgentScope> validScopes = agentType != null && agentType.equalsIgnoreCase("external")
                ? List.of(AgentScope.EXTERNAL, AgentScope.BOTH)
                : List.of(AgentScope.INTERNAL, AgentScope.BOTH);

        // Critério de visibilidade para web agents:
        // - Mensagens repetidas: sempre visíveis enquanto ativas (o cliente faz dedup
        // por id+lastSentAt)
        // - Mensagens únicas com publishedAt: visíveis se publicadas após 'since'
        // - Mensagens imediatas (sem publishedAt): visíveis se criadas após 'since'
        // - Nunca despachadas (lastSentAt IS NULL): garantia de entrega no primeiro
        // poll
        // Nota: lastSentAt é timestamp do broker (RabbitMQ) e não deve ser usado como
        // filtro
        // de visibilidade para web agents — os dois canais têm semânticas
        // independentes.
        String jpql = """
                SELECT DISTINCT m FROM Message m
                LEFT JOIN FETCH m.level l
                LEFT JOIN FETCH m.type t
                LEFT JOIN FETCH m.user u
                LEFT JOIN m.departments d
                WHERE (m.expireAt IS NULL OR m.expireAt > :now)
                AND (m.publishedAt IS NULL OR m.publishedAt <= :now)
                AND m.agentScope IN :scopes
                AND (:departmentName IS NULL OR :departmentName = ''
                     OR LOWER(d.name) = :departmentName OR m.departments IS EMPTY)
                AND (
                    m.repeatIntervalMinutes IS NOT NULL
                    OR (m.scheduleDaysOfWeek IS NOT NULL)
                    OR (m.scheduleMonthDays IS NOT NULL)
                    OR (m.publishedAt IS NOT NULL AND m.publishedAt >= :since)
                    OR (m.publishedAt IS NULL AND m.createdAt >= :since)
                    OR m.lastSentAt IS NULL
                )
                ORDER BY COALESCE(m.publishedAt, m.createdAt) DESC
                """;

        List<Message> messages = entityManager.createQuery(jpql, Message.class)
                .setParameter("since", since)
                .setParameter("now", now)
                .setParameter("scopes", validScopes)
                .setParameter("departmentName",
                        departmentName != null ? departmentName.toLowerCase().replace(" ", "_") : "")
                .getResultList();

        return messages.stream().map(m -> {
            AgentMessageDto dto = new AgentMessageDto();
            dto.setId(m.getId());
            dto.setTitle(m.getTitle());
            dto.setContent(m.getContent());
            dto.setLevel(m.getLevel() != null ? m.getLevel().getName() : null);
            dto.setType(m.getType() != null ? m.getType().getName() : null);
            dto.setUser(m.getUser() != null ? m.getUser().getUsername() : null);
            dto.setPublishedAt(m.getPublishedAt());
            dto.setExpireAt(m.getExpireAt());
            dto.setLastSentAt(m.getLastSentAt());
            if (m.getDepartments() != null) {
                dto.setDepartments(m.getDepartments().stream()
                        .map(dep -> new AgentMessageDto.DepartmentInfo(dep.getId(), dep.getName()))
                        .toList());
            }
            return dto;
        }).toList();
    }

    /**
     * Verifica se algum horário agendado em scheduleTimes já passou hoje e ainda
     * não foi enviado neste slot.
     * Se scheduleTimes for nulo/vazio → comportamento padrão: dispara uma vez por
     * dia.
     * Com múltiplos horários (ex: "09:00,14:00"): dispara a cada slot que passou,
     * verificando se lastSentAt é anterior ao horário do slot mais recente que
     * passou.
     */
    private boolean isTimeSlotReached(String scheduleTimes, LocalDateTime lastSentAt, LocalDate today,
            LocalTime nowTime) {
        if (scheduleTimes == null || scheduleTimes.isBlank()) {
            // Sem horário fixo: dispara uma vez por dia
            return lastSentAt == null || !lastSentAt.toLocalDate().equals(today);
        }
        LocalTime latestPassedSlot = null;
        for (String ts : scheduleTimes.split(",")) {
            try {
                LocalTime slot = LocalTime.parse(ts.trim());
                if (!nowTime.isBefore(slot)) { // nowTime >= slot
                    if (latestPassedSlot == null || slot.isAfter(latestPassedSlot)) {
                        latestPassedSlot = slot;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (latestPassedSlot == null)
            return false; // nenhum horário passou ainda
        if (lastSentAt == null)
            return true;
        // Dispara se ainda não enviou desde o slot mais recente que passou hoje
        LocalDateTime threshold = LocalDateTime.of(today, latestPassedSlot);
        return lastSentAt.isBefore(threshold);
    }

    /**
     * Resolve os horários do dia atual a partir de dois formatos suportados:
     * - legado: CSV simples (ex.: "09:00,14:00") aplicado a todos os dias
     * - novo: JSON por dia (ex.: [{"day":"1","times":["09:00","14:00"]}])
     */
    private String resolveScheduleTimesForDay(String scheduleTimes, String dayKey) {
        if (scheduleTimes == null || scheduleTimes.isBlank()) {
            return null;
        }

        String trimmed = scheduleTimes.trim();
        if (!trimmed.startsWith("[")) {
            return scheduleTimes;
        }

        try {
            ObjectMapper om = new ObjectMapper();
            List<Map<String, Object>> groupedTimes = om.readValue(trimmed,
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> groupedTime : groupedTimes) {
                if (groupedTime == null || groupedTime.get("day") == null) {
                    continue;
                }

                String storedDay = groupedTime.get("day").toString().trim();
                if (!storedDay.equals(dayKey)) {
                    continue;
                }

                Object timesValue = groupedTime.get("times");
                if (!(timesValue instanceof List<?> timesList) || timesList.isEmpty()) {
                    return null;
                }

                List<String> normalizedTimes = new ArrayList<>();
                for (Object time : timesList) {
                    if (time == null) {
                        continue;
                    }
                    String normalized = time.toString().trim();
                    if (!normalized.isBlank()) {
                        normalizedTimes.add(normalized);
                    }
                }

                return normalizedTimes.isEmpty() ? null : String.join(",", normalizedTimes);
            }

            return null;
        } catch (IOException | RuntimeException e) {
            return scheduleTimes;
        }
    }

    /**
     * Verifica se o momento atual está dentro de alguma janela de disponibilidade.
     * Retorna true se nenhuma janela estiver definida (sem restrição) ou se o
     * horário
     * atual cair em uma das janelas configuradas.
     * Formato JSON: [{"day":1,"startTime":"08:00","endTime":"12:00"}, ...]
     * day: 1=Seg, 7=Dom (ISO day of week)
     */
    private boolean isWithinAvailabilityWindows(String availabilityWindowsJson) {
        String effectiveWindowsJson = (availabilityWindowsJson != null && !availabilityWindowsJson.isBlank())
                ? availabilityWindowsJson
                : defaultOfficeHoursWindowsJson;

        if (effectiveWindowsJson == null || effectiveWindowsJson.isBlank()) {
            return true; // sem restrição (fallback desabilitado)
        }

        try {
            ObjectMapper om = new ObjectMapper();
            List<Map<String, Object>> windows = om.readValue(effectiveWindowsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            int todayIso = LocalDate.now().getDayOfWeek().getValue();
            LocalTime nowTime = LocalTime.now();
            for (Map<String, Object> window : windows) {
                Object dayVal = window.get("day");
                if (dayVal == null)
                    continue;
                int day = Integer.parseInt(dayVal.toString());
                if (day != todayIso)
                    continue;
                if (window.get("startTime") == null || window.get("endTime") == null) {
                    continue;
                }
                LocalTime start = LocalTime.parse(window.get("startTime").toString());
                LocalTime end = LocalTime.parse(window.get("endTime").toString());
                if (!nowTime.isBefore(start) && nowTime.isBefore(end)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return true; // falha aberta: se JSON inválido, não bloqueia
        }
    }
}
