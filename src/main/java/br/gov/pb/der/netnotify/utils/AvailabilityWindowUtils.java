package br.gov.pb.der.netnotify.utils;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AvailabilityWindowUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AvailabilityWindowUtils() {
    }

    public record AvailabilityWindow(int day, LocalTime startTime, LocalTime endTime) {
    }

    public static List<AvailabilityWindow> parse(String json) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        if (root == null || !root.isArray()) {
            throw new IOException("Formato de janelas de disponibilidade inválido.");
        }

        List<AvailabilityWindow> windows = new ArrayList<>();
        for (JsonNode entry : root) {
            appendWindows(entry, windows);
        }
        return windows;
    }

    public static String normalizeToFlatJson(String json) throws IOException {
        List<Map<String, String>> normalized = parse(json).stream()
                .map(window -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("day", String.valueOf(window.day()));
                    item.put("startTime", window.startTime().toString());
                    item.put("endTime", window.endTime().toString());
                    return item;
                })
                .toList();

        return OBJECT_MAPPER.writeValueAsString(normalized);
    }

    private static void appendWindows(JsonNode entry, List<AvailabilityWindow> windows) {
        if (entry == null || !entry.isObject()) {
            throw new IllegalArgumentException("Janela de disponibilidade inválida.");
        }

        int day = parseDay(entry.get("day"));
        JsonNode intervals = entry.get("intervals");
        if (intervals != null && !intervals.isNull()) {
            if (!intervals.isArray()) {
                throw new IllegalArgumentException("Lista de intervalos inválida.");
            }
            for (JsonNode interval : intervals) {
                windows.add(parseWindow(day, interval));
            }
            return;
        }

        windows.add(parseWindow(day, entry));
    }

    private static AvailabilityWindow parseWindow(int day, JsonNode entry) {
        LocalTime startTime = parseTime(entry.get("startTime"));
        LocalTime endTime = parseTime(entry.get("endTime"));
        return new AvailabilityWindow(day, startTime, endTime);
    }

    private static int parseDay(JsonNode dayNode) {
        if (dayNode == null || dayNode.isNull()) {
            throw new IllegalArgumentException("Dia da janela de disponibilidade é obrigatório.");
        }

        try {
            return Integer.parseInt(dayNode.asText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Dia da janela de disponibilidade inválido.", ex);
        }
    }

    private static LocalTime parseTime(JsonNode timeNode) {
        if (timeNode == null || timeNode.isNull()) {
            throw new IllegalArgumentException("Horário da janela de disponibilidade é obrigatório.");
        }

        return LocalTime.parse(timeNode.asText().trim());
    }
}
