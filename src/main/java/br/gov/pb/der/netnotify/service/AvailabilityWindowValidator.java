package br.gov.pb.der.netnotify.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

import br.gov.pb.der.netnotify.utils.AvailabilityWindowUtils;

@Component
public class AvailabilityWindowValidator {

    private static final String INVALID_FORMAT_MESSAGE = "Formato de janelas de disponibilidade inválido.";

    public List<String> validate(String availabilityWindowsJson) {
        List<String> errors = new ArrayList<>();
        String normalizedWindows = normalizeBlankAsEmptyList(availabilityWindowsJson);
        List<AvailabilityWindowUtils.AvailabilityWindow> windows;

        try {
            windows = AvailabilityWindowUtils.parse(normalizedWindows);
        } catch (IOException | RuntimeException ex) {
            errors.add(INVALID_FORMAT_MESSAGE);
            return errors;
        }

        Map<Integer, List<int[]>> intervalsByDay = new HashMap<>();

        for (AvailabilityWindowUtils.AvailabilityWindow window : windows) {
            int day = window.day();
            if (day < 1 || day > 7) {
                errors.add("Dia da janela de disponibilidade deve estar entre 1 e 7.");
                return errors;
            }

            if (!window.startTime().isBefore(window.endTime())) {
                errors.add("Horário inicial deve ser menor que o horário final nas janelas de disponibilidade.");
                return errors;
            }

            intervalsByDay.computeIfAbsent(day, ignored -> new ArrayList<>())
                    .add(new int[] { window.startTime().toSecondOfDay(), window.endTime().toSecondOfDay() });
        }

        for (Map.Entry<Integer, List<int[]>> entry : intervalsByDay.entrySet()) {
            List<int[]> intervals = entry.getValue();
            intervals.sort(Comparator.comparingInt(interval -> interval[0]));
            for (int index = 1; index < intervals.size(); index++) {
                int[] previous = intervals.get(index - 1);
                int[] current = intervals.get(index);
                if (current[0] < previous[1]) {
                    errors.add("Existem janelas de disponibilidade sobrepostas no mesmo dia.");
                    return errors;
                }
            }
        }

        return errors;
    }

    public void validate(String fieldName, String availabilityWindowsJson, BindingResult bindingResult) {
        for (String error : validate(availabilityWindowsJson)) {
            bindingResult.rejectValue(fieldName, "Invalid", error);
        }
    }

    public String normalizeAndValidate(String availabilityWindowsJson) {
        String normalizedInput = normalizeBlankAsEmptyList(availabilityWindowsJson);
        String normalizedJson;

        try {
            normalizedJson = AvailabilityWindowUtils.normalizeToFlatJson(normalizedInput);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException(INVALID_FORMAT_MESSAGE, ex);
        }

        List<String> errors = validate(normalizedJson);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }

        return normalizedJson;
    }

    private String normalizeBlankAsEmptyList(String availabilityWindowsJson) {
        return availabilityWindowsJson == null || availabilityWindowsJson.isBlank() ? "[]" : availabilityWindowsJson;
    }
}
