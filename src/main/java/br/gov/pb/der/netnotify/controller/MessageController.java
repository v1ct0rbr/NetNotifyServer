package br.gov.pb.der.netnotify.controller;

import java.io.IOException;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import br.gov.pb.der.netnotify.service.DepartmentService;
import br.gov.pb.der.netnotify.service.LevelService;
import br.gov.pb.der.netnotify.service.MessageService;
import br.gov.pb.der.netnotify.service.MessageTypeService;
import br.gov.pb.der.netnotify.service.RabbitmqService;
import br.gov.pb.der.netnotify.service.UserService;
import br.gov.pb.der.netnotify.utils.Functions;
import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    public static final int DEFAULT_PAGE_SIZE = 10;

    private final MessageService messageService;

    private final UserService userService;

    private final LevelService levelService;

    private final MessageTypeService messageTypeService;

    private final DepartmentService departmentService;

    private final RabbitmqService rabbitmqService;

    @GetMapping("/get-default-office-hours-window")
    public ResponseEntity<SimpleResponseUtils<String>> getDefaultOfficeHoursWindow() {
        String defaultOfficeHoursWindow = messageService.getDefaultOfficeHoursWindow();
        return ResponseEntity.ok(SimpleResponseUtils.success(defaultOfficeHoursWindow,
                "Janela de horário comercial padrão obtida com sucesso."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponseUtils<MessageResponseDto>> getMessageById(@PathVariable UUID id) {
        Message message = messageService.findById(id);

        if (message == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(SimpleResponseUtils.error(null, "Mensagem não encontrada."));
        }
        return ResponseEntity
                .ok(SimpleResponseUtils.success(message.objectMapper(), "Mensagem encontrada com sucesso."));
    }

    // parametros de url .... clone-message-id
    @GetMapping(value = { "/", "" }, params = "clone-message-id")
    public ResponseEntity<MessageDto> getSimpleMessage(@RequestParam(name = "clone-message-id") UUID id) {
        MessageDto messageDto = messageService.findMessageDtoById(id);
        if (messageDto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(messageDto);
    }

    @PostMapping("/create")
    public ResponseEntity<SimpleResponseUtils<?>> saveMessage(@RequestBody @Valid MessageDto messageDto,
            BindingResult bindingResult) {

        Boolean isMessageValid = isMessageValid(messageDto, bindingResult);

        if (!isMessageValid) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null, Functions.errorStringfy(bindingResult)));
        }

        try {
            Message message = new Message();
            message.setTitle(messageDto.getTitle());
            message.setContent(messageDto.getContent());
            message.setLevel(levelService.findById(messageDto.getLevel()));
            message.setType(messageTypeService.findById(messageDto.getType()));
            // Garante que o usuário local exista (cria se ainda não sincronizado)
            message.setUser(userService.getOrCreateUser());
            message.setSendToSubdivisions(messageDto.getSendToSubdivisions());
            message.setAgentScope(br.gov.pb.der.netnotify.model.AgentScope.BOTH);
            message.setExpireAt(messageDto.getExpireAt());
            message.setPublishedAt(messageDto.getPublishedAt());
            // Valor 0 significa "sem repetição" — trata como null para não disparar o
            // scheduler
            Integer interval = messageDto.getRepeatIntervalMinutes();
            message.setRepeatIntervalMinutes((interval != null && interval > 0) ? interval : null);
            // Campos de programação por horário fixo
            if (messageDto.getScheduleDaysOfWeek() != null && !messageDto.getScheduleDaysOfWeek().isBlank()) {
                message.setScheduleDaysOfWeek(messageDto.getScheduleDaysOfWeek());
                message.setScheduleTimes(messageDto.getScheduleTimes());
                message.setRepeatIntervalMinutes(null);
            } else if (messageDto.getScheduleMonthDays() != null && !messageDto.getScheduleMonthDays().isBlank()) {
                message.setScheduleMonthDays(messageDto.getScheduleMonthDays());
                message.setScheduleTimes(messageDto.getScheduleTimes());
                message.setRepeatIntervalMinutes(null);
            }
            if (messageDto.getAvailabilityWindows() != null && !messageDto.getAvailabilityWindows().isBlank()) {
                message.setAvailabilityWindows(messageDto.getAvailabilityWindows());
            }
            message.setDepartments(messageDto.getDepartments().stream()
                    .map(deptId -> departmentService.findById(deptId))
                    .filter(dept -> dept != null)
                    .toList());

            messageService.save(message);
            boolean hasSchedule = (message.getScheduleDaysOfWeek() != null || message.getScheduleMonthDays() != null);
            boolean isDelayed = message.getPublishedAt() != null
                    && message.getPublishedAt().isAfter(LocalDateTime.now());
            if (!hasSchedule && !isDelayed) {
                messageService.sendNotification(message);
            }
            return ResponseEntity.ok(SimpleResponseUtils.success(message.getId(), "Mensagem salva com sucesso."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SimpleResponseUtils.error(null, "Erro ao salvar a mensagem: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Page<MessageResponseDto>> getAllMessages(
            @ModelAttribute MessageFilter filter) {
        try {
            int pageNumber = (filter.getPage() != null && filter.getPage() > 0) ? filter.getPage() : 0;
            int pageSize = (filter.getSize() != null && filter.getSize() > 0) ? filter.getSize() : DEFAULT_PAGE_SIZE;
            Pageable pageable = Pageable.ofSize(pageSize).withPage(pageNumber);
            Page<MessageResponseDto> messages = messageService.findAllMessages(filter, pageable);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping(path = "/delete", params = "id")
    public ResponseEntity<SimpleResponseUtils<?>> deleteMessage(@RequestParam UUID id) {
        Message message = messageService.findById(id);
        if (message == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(SimpleResponseUtils.error(null, "Mensagem não encontrada."));
        }
        messageService.delete(message);
        return ResponseEntity.ok(SimpleResponseUtils.success(null, "Mensagem deletada com sucesso."));
    }

    public boolean isMessageValid(MessageDto messageDto, BindingResult bindingResult) {
        LocalDateTime now = LocalDateTime.now();

        if (messageDto.getExpireAt() != null && messageDto.getExpireAt().isBefore(now)) {
            bindingResult.rejectValue("expireAt", "Invalid", "A data de expiração deve ser no futuro.");
        }
        if (messageDto.getPublishedAt() != null && messageDto.getPublishedAt().isBefore(now)) {
            bindingResult.rejectValue("publishedAt", "Invalid", "A data de publicação deve ser no futuro.");
        }
        if (messageDto.getRepeatIntervalMinutes() != null
                && !Functions.isNumberValid(messageDto.getRepeatIntervalMinutes())) {
            bindingResult.rejectValue("repeatIntervalMinutes", "Invalid",
                    "O intervalo de repetição deve ser maior que zero.");
        }
        // scheduleDaysOfWeek e scheduleMonthDays são mutuamente exclusivos
        boolean hasDays = messageDto.getScheduleDaysOfWeek() != null && !messageDto.getScheduleDaysOfWeek().isBlank();
        boolean hasMonthDays = messageDto.getScheduleMonthDays() != null
                && !messageDto.getScheduleMonthDays().isBlank();
        if (hasDays && hasMonthDays) {
            bindingResult.rejectValue("scheduleDaysOfWeek", "Invalid",
                    "Não é possível combinar agendamento semanal e mensal.");
        }

        if (messageDto.getAvailabilityWindows() != null && !messageDto.getAvailabilityWindows().isBlank()) {
            validateAvailabilityWindows(messageDto.getAvailabilityWindows(), bindingResult);
        }

        return !bindingResult.hasErrors();
    }

    private void validateAvailabilityWindows(String availabilityWindowsJson, BindingResult bindingResult) {
        List<Map<String, Object>> windows;
        try {
            ObjectMapper om = new ObjectMapper();
            windows = om.readValue(availabilityWindowsJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException | RuntimeException ex) {
            bindingResult.rejectValue("availabilityWindows", "Invalid",
                    "Formato de janelas de disponibilidade inválido.");
            return;
        }

        Map<Integer, List<int[]>> intervalsByDay = new HashMap<>();

        for (int i = 0; i < windows.size(); i++) {
            Map<String, Object> window = windows.get(i);
            if (window == null) {
                bindingResult.rejectValue("availabilityWindows", "Invalid",
                        "Janela de disponibilidade inválida.");
                continue;
            }

            Integer day = parseDay(window.get("day"));
            if (day == null || day < 1 || day > 7) {
                bindingResult.rejectValue("availabilityWindows", "Invalid",
                        "Dia da janela de disponibilidade deve estar entre 1 e 7.");
                continue;
            }

            String startTimeRaw = window.get("startTime") != null ? window.get("startTime").toString() : null;
            String endTimeRaw = window.get("endTime") != null ? window.get("endTime").toString() : null;

            LocalTime start = parseTime(startTimeRaw);
            LocalTime end = parseTime(endTimeRaw);

            if (start == null || end == null) {
                bindingResult.rejectValue("availabilityWindows", "Invalid",
                        "Horários da janela de disponibilidade devem estar no formato HH:mm.");
                continue;
            }

            if (!start.isBefore(end)) {
                bindingResult.rejectValue("availabilityWindows", "Invalid",
                        "Horário inicial deve ser menor que o horário final nas janelas de disponibilidade.");
                continue;
            }

            intervalsByDay.computeIfAbsent(day, ignored -> new ArrayList<>())
                    .add(new int[] { start.toSecondOfDay(), end.toSecondOfDay() });
        }

        for (Map.Entry<Integer, List<int[]>> entry : intervalsByDay.entrySet()) {
            List<int[]> intervals = entry.getValue();
            intervals.sort(Comparator.comparingInt(interval -> interval[0]));
            for (int i = 1; i < intervals.size(); i++) {
                int[] previous = intervals.get(i - 1);
                int[] current = intervals.get(i);
                if (current[0] < previous[1]) {
                    bindingResult.rejectValue("availabilityWindows", "Invalid",
                            "Existem janelas de disponibilidade sobrepostas no mesmo dia.");
                    return;
                }
            }
        }
    }

    private Integer parseDay(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

}
