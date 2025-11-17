package br.gov.pb.der.netnotify.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.CountByItemDto;
import br.gov.pb.der.netnotify.dto.DashboardDataDto;
import br.gov.pb.der.netnotify.service.MessageService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MessageService messageService;
   
    @GetMapping(value = { "", "/summary" }, produces = { "application/json" })
    private ResponseEntity<?> getDashboardData() {
        Long totalMessages = messageService.countTotalMessages();
        Map<String, Long> totalMessagesByLevel = messageService.countTotalMessagesByLevel();
        Map<String, Long> totalMessagesByType = messageService.countTotalMessagesByType();

        // Convert Maps to Lists of CountByItemDto, filtering out null keys
        List<CountByItemDto> levelCounts = totalMessagesByLevel.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .map(entry -> {
                Long count = entry.getValue() instanceof Long ? (Long) entry.getValue() : Long.parseLong(entry.getValue().toString());
                return new CountByItemDto(entry.getKey(), count);
            })
            .collect(Collectors.toList());

        List<CountByItemDto> typeCounts = totalMessagesByType.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .map(entry -> {
                Long count = entry.getValue() instanceof Long ? (Long) entry.getValue() : Long.parseLong(entry.getValue().toString());
                return new CountByItemDto(entry.getKey(), count);
            })
            .collect(Collectors.toList());

        // Construct and return the dashboard data response
        DashboardDataDto dashboardData = new DashboardDataDto();
        dashboardData.setTotalMessages(totalMessages);
        dashboardData.setTotalMessagesByLevel(levelCounts);
        dashboardData.setTotalMessagesByType(typeCounts);

        return ResponseEntity.ok(dashboardData);

    }

}
