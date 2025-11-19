package br.gov.pb.der.netnotify.controller;

import java.time.LocalDateTime;
import java.util.UUID;

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
            message.setExpireAt(messageDto.getExpireAt());
            message.setPublishedAt(messageDto.getPublishedAt());
            message.setRepeatIntervalMinutes(messageDto.getRepeatIntervalMinutes());
            message.setDepartments(messageDto.getDepartments().stream()
                    .map(deptId -> departmentService.findById(deptId))
                    .filter(dept -> dept != null)
                    .toList());

            messageService.save(message);
            if (message.getPublishedAt() == null
                    || (message.getPublishedAt() != null && !message.getPublishedAt().isAfter(LocalDateTime.now()))) {
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

        if (messageDto.getExpireAt() != null && !Functions.isNumberValid(messageDto.getRepeatIntervalMinutes())) {
            bindingResult.rejectValue("repeatIntervalMinutes", "Invalid",
                    "O intervalo de repetição deve ser maior ou igual a zero.");
        }

        return !bindingResult.hasErrors();
    }

}
