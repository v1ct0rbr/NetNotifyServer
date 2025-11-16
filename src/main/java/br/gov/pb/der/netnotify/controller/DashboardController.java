package br.gov.pb.der.netnotify.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.DashboardDataDto;
import br.gov.pb.der.netnotify.service.LevelService;
import br.gov.pb.der.netnotify.service.MessageService;
import br.gov.pb.der.netnotify.service.MessageTypeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private MessageService messageService;

    private LevelService levelService;

    private MessageTypeService messageTypeService;

    @GetMapping(value = { "", "/" }, produces = { "application/json" })
    private ResponseEntity<?> getDashboardData() {
        Long totalMessages = messageService.countTotalMessages();
        Map<String, Long> totalMessagesByLevel = messageService.countTotalMessagesByLevel();
        Map<String, Long> totalMessagesByType = messageService.countTotalMessagesByType();

        // Construct and return the dashboard data response
        DashboardDataDto dashboardData = new DashboardDataDto();
        dashboardData.setTotalMessages(totalMessages);
        dashboardData.setTotalMessagesByLevel(totalMessagesByLevel);
        dashboardData.setTotalMessagesByType(totalMessagesByType);

        return ResponseEntity.ok(dashboardData);

    }

}
