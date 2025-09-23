package br.gov.pb.der.netnotify.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.model.Level;
import br.gov.pb.der.netnotify.model.MessageType;
import br.gov.pb.der.netnotify.service.LevelService;
import br.gov.pb.der.netnotify.service.MessageTypeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aux/")
@RequiredArgsConstructor
public class AuxController {

    private final MessageTypeService messageTypeService;
    private final LevelService levelService;

    @GetMapping("/message-types")
    public ResponseEntity<List<MessageType>> getAllMessageTypes() {
        List<MessageType> messageTypes = messageTypeService.findAll();
        return ResponseEntity.ok(messageTypes);
    }

    @GetMapping("/levels")
    public ResponseEntity<List<Level>> getAllLevels() {
        List<Level> levels = levelService.findAll();
        return ResponseEntity.ok(levels);
    }

}
