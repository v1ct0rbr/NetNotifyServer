package br.gov.pb.der.netnotify.controller;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.model.Message;

@RestController
@RequestMapping("/notify")
public class NotificationController {

    private final RabbitTemplate rabbitTemplate;
    private final TopicExchange fanoutExchange;

    public NotificationController(RabbitTemplate rabbitTemplate, TopicExchange fanoutExchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.fanoutExchange = fanoutExchange;
    }

    @PostMapping
    public String sendNotification(@RequestBody Message message) {
        rabbitTemplate.convertAndSend(fanoutExchange.getName(), "broadcast.general", message);
        return "Mensagem enviada: " + message;
    }
}
