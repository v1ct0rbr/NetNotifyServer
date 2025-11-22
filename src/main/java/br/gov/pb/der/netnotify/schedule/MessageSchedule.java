package br.gov.pb.der.netnotify.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.gov.pb.der.netnotify.service.CacheService;
import br.gov.pb.der.netnotify.service.MessageService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageSchedule {

    private final MessageService messageService;

    private final CacheService cacheService;

    @Scheduled(fixedRate = 60000) // Executa a cada 60 segundos
    public void sendScheduledMessages() {
        System.out.println("Verificando mensagens agendadas para envio...");
        messageService.sendScheduledMessages();
    }

    @Scheduled(fixedRate = 1800000) // Executa a cada 30 minutos
    public void clearCache() {
        System.out.println("Limpando cache de mensagens...");
        cacheService.evictAllCaches();
    }

}
