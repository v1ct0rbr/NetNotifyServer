package br.gov.pb.der.netnotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AvailabilityWindowValidatorTest {

    private final AvailabilityWindowValidator validator = new AvailabilityWindowValidator();

    @Test
    void acceptsBlankValueAsNoRestriction() {
        assertThat(validator.validate("")).isEmpty();
        assertThat(validator.normalizeAndValidate("")).isEqualTo("[]");
    }

    @Test
    void rejectsOverlappingWindowsOnSameDay() {
        String json = """
                [{"day":"1","startTime":"08:00","endTime":"12:00"},{"day":"1","startTime":"11:00","endTime":"13:00"}]
                """;

        assertThat(validator.validate(json))
                .containsExactly("Existem janelas de disponibilidade sobrepostas no mesmo dia.");
    }

    @Test
    void rejectsInvalidTimeRange() {
        String json = """
                [{"day":"1","startTime":"17:00","endTime":"08:00"}]
                """;

        assertThatThrownBy(() -> validator.normalizeAndValidate(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Horário inicial deve ser menor que o horário final nas janelas de disponibilidade.");
    }
}
