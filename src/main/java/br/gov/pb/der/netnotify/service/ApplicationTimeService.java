package br.gov.pb.der.netnotify.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApplicationTimeService {

    private final ZoneId zoneId;

    public ApplicationTimeService(@Value("${app.timezone:America/Recife}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    public LocalDateTime nowDateTime() {
        return LocalDateTime.now(zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public LocalTime nowTime() {
        return LocalTime.now(zoneId);
    }
}
