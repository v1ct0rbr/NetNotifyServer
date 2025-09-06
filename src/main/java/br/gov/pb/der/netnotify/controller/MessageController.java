package br.gov.pb.der.netnotify.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.MessageDto;
import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.response.MessageResponseDto;
import br.gov.pb.der.netnotify.service.LevelService;
import br.gov.pb.der.netnotify.service.MessageService;
import br.gov.pb.der.netnotify.service.MessageTypeService;
import br.gov.pb.der.netnotify.service.UserService;
import br.gov.pb.der.netnotify.utils.Functions;
import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    private final UserService userService;

    private final LevelService levelService;

    private final MessageTypeService messageTypeService;

    @GetMapping(value = { "/", "" })
    public ResponseEntity<SimpleResponseUtils<?>> getMethodName() {
        return ResponseEntity.ok(SimpleResponseUtils.success("Hello World"));
    }

    @PostMapping("/create")
    public ResponseEntity<SimpleResponseUtils<?>> saveMessage(@RequestBody @Validated MessageDto messageDto,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null, Functions.errorStringfy(bindingResult)));
        }
        Message message = new Message();
        message.setContent(messageDto.getContent());
        message.setLevel(levelService.findById(messageDto.getLevelId()));
        message.setType(messageTypeService.findById(messageDto.getMessageTypeId()));
        message.setUser(userService.getLoggedUser());
        messageService.save(message);
        return ResponseEntity.ok(SimpleResponseUtils.success(message.getId(), "Mensagem salva com sucesso."));
    }

    @GetMapping("/all")
    public ResponseEntity<SimpleResponseUtils<Page<MessageResponseDto>>> getAllMessages(
            @ModelAttribute MessageFilter filter,
            Pageable pageable) {
        Page<MessageResponseDto> messages = messageService.findAllMessages(filter, pageable);
        return ResponseEntity.ok(SimpleResponseUtils.success(messages));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<SimpleResponseUtils<?>> deleteMessage(@RequestParam UUID id) {
        Message message = messageService.findById(id);
        if (message == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(SimpleResponseUtils.error(null, "Mensagem não encontrada."));
        }
        messageService.delete(message);
        return ResponseEntity.ok(SimpleResponseUtils.success(null, "Mensagem deletada com sucesso."));
    }

}
