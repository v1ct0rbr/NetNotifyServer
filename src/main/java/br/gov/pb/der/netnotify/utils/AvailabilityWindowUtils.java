package br.gov.pb.der.netnotify.utils;

import java.io.IOException;
import java.time.LocalDate;
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

    public record AvailabilityDayRule(int day, boolean ignored, List<AvailabilityWindow> windows) {
        public boolean allows(LocalTime time) {
            if (ignored) {
                return false;
            }

            for (AvailabilityWindow window : windows) {
                if (!time.isBefore(window.startTime()) && time.isBefore(window.endTime())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static List<AvailabilityWindow> parse(String json) throws IOException {
        return parseDayRules(json).values().stream()
                .flatMap(rule -> rule.windows().stream())
                .toList();
    }

    public static Map<Integer, AvailabilityDayRule> parseDayRules(String json) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        if (root == null || !root.isArray()) {
            throw new IOException("Formato de janelas de disponibilidade inválido.");
        }

        Map<Integer, DayRuleBuilder> builders = new LinkedHashMap<>();
        for (JsonNode entry : root) {
            appendDayRule(entry, builders);
        }

        Map<Integer, AvailabilityDayRule> rules = new LinkedHashMap<>();
        for (DayRuleBuilder builder : builders.values()) {
            rules.put(builder.day, new AvailabilityDayRule(builder.day, builder.ignored, List.copyOf(builder.windows)));
        }
        return rules;
    }

    public static String normalizeToFlatJson(String json) throws IOException {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (AvailabilityDayRule rule : parseDayRules(json).values()) {
            if (rule.ignored()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("day", String.valueOf(rule.day()));
                item.put("ignored", true);
                normalized.add(item);
                continue;
            }

            for (AvailabilityWindow window : rule.windows()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("day", String.valueOf(window.day()));
                item.put("startTime", window.startTime().toString());
                item.put("endTime", window.endTime().toString());
                normalized.add(item);
            }
        }

        return OBJECT_MAPPER.writeValueAsString(normalized);
    }

    public static boolean isAllowedAt(String messageWindowsJson, String defaultWindowsJson, LocalDate today,
            LocalTime nowTime) throws IOException {
        int todayIso = today.getDayOfWeek().getValue();

        Map<Integer, AvailabilityDayRule> messageRules = parseDayRulesOrEmpty(messageWindowsJson);
        AvailabilityDayRule messageRule = messageRules.get(todayIso);
        if (messageRule != null) {
            return messageRule.allows(nowTime);
        }

        Map<Integer, AvailabilityDayRule> defaultRules = parseDayRulesOrEmpty(defaultWindowsJson);
        if (defaultRules.isEmpty()) {
            return true;
        }

        AvailabilityDayRule defaultRule = defaultRules.get(todayIso);
        if (defaultRule == null) {
            return false;
        }

        return defaultRule.allows(nowTime);
    }

    private static Map<Integer, AvailabilityDayRule> parseDayRulesOrEmpty(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return parseDayRules(json);
    }

    private static void appendDayRule(JsonNode entry, Map<Integer, DayRuleBuilder> builders) {
        if (entry == null || !entry.isObject()) {
            throw new IllegalArgumentException("Janela de disponibilidade inválida.");
        }

        int day = parseDay(entry.get("day"));
        DayRuleBuilder builder = builders.computeIfAbsent(day, DayRuleBuilder::new);
        boolean ignored = entry.path("ignored").asBoolean(false);
        JsonNode intervals = entry.get("intervals");

        if (ignored) {
            if ((intervals != null && intervals.isArray() && intervals.size() > 0)
                    || hasValue(entry.get("startTime"))
                    || hasValue(entry.get("endTime"))) {
                throw new IllegalArgumentException(
                        "Dia desconsiderado não pode conter intervalos de disponibilidade.");
            }
            builder.markIgnored();
            return;
        }

        if (intervals != null && !intervals.isNull()) {
            if (!intervals.isArray()) {
                throw new IllegalArgumentException("Lista de intervalos inválida.");
            }
            for (JsonNode interval : intervals) {
                builder.addWindow(parseWindow(day, interval));
            }
            return;
        }

        builder.addWindow(parseWindow(day, entry));
    }

    private static AvailabilityWindow parseWindow(int day, JsonNode entry) {
        LocalTime startTime = parseTime(entry.get("startTime"));
        LocalTime endTime = parseTime(entry.get("endTime"));
        return new AvailabilityWindow(day, startTime, endTime);
    }

    private static boolean hasValue(JsonNode valueNode) {
        return valueNode != null && !valueNode.isNull() && !valueNode.asText().trim().isEmpty();
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

    private static final class DayRuleBuilder {
        private final int day;
        private boolean ignored;
        private final List<AvailabilityWindow> windows = new ArrayList<>();

        private DayRuleBuilder(int day) {
            this.day = day;
        }

        private void markIgnored() {
            if (!windows.isEmpty()) {
                throw new IllegalArgumentException(
                        "Dia desconsiderado não pode ter intervalos personalizados.");
            }
            this.ignored = true;
        }

        private void addWindow(AvailabilityWindow window) {
            if (ignored) {
                throw new IllegalArgumentException(
                        "Dia desconsiderado não pode ter intervalos personalizados.");
            }
            windows.add(window);
        }
    }
}
