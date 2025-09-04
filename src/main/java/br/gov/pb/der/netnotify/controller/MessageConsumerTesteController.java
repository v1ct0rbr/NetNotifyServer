package br.gov.pb.der.netnotify.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.service.RabbitmqService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/test-consumer")
@RequiredArgsConstructor
public class MessageConsumerTesteController {

    private final RabbitmqService rabbitmqService;

    @PostMapping(value = {"/", ""})
    public ResponseEntity<String> testPublish(HttpServletRequest request) {
        String message = request.getParameter("message");
        rabbitmqService.basicPublish(message);
        return ResponseEntity.ok("Produtor de mensagens está funcionando!");
    }

    @GetMapping(value = {"/", ""})
    public ResponseEntity<String> testConsume(@RequestParam(required = false) Long timeout) {
        long t = timeout != null ? timeout : 5000L;
        String msg = rabbitmqService.basicConsume(t);
        if (msg == null) {
            return ResponseEntity.ok("No message received within " + t + " ms");
        }
        return ResponseEntity.ok(msg);
    }
}
