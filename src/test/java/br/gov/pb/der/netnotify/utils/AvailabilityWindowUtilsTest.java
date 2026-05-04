package br.gov.pb.der.netnotify.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class AvailabilityWindowUtilsTest {

    @Test
    void parsesGroupedIntervalsIntoFlatWindows() throws Exception {
        String json = """
                [{"day":"1","intervals":[{"startTime":"08:00","endTime":"12:00"},{"startTime":"13:30","endTime":"16:00"}]}]
                """;

        List<AvailabilityWindowUtils.AvailabilityWindow> windows = AvailabilityWindowUtils.parse(json);

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).day()).isEqualTo(1);
        assertThat(windows.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(windows.get(0).endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(windows.get(1).day()).isEqualTo(1);
        assertThat(windows.get(1).startTime()).isEqualTo(LocalTime.of(13, 30));
        assertThat(windows.get(1).endTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void normalizesGroupedIntervalsToExistingFlatJsonShape() throws Exception {
        String json = """
                [{"day":"1","intervals":[{"startTime":"08:00","endTime":"12:00"},{"startTime":"13:30","endTime":"16:00"}]},{"day":"2","intervals":[{"startTime":"09:00","endTime":"17:00"}]}]
                """;

        String normalized = AvailabilityWindowUtils.normalizeToFlatJson(json);

        assertThat(normalized).isEqualTo(
                "[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"12:00\"},{\"day\":\"1\",\"startTime\":\"13:30\",\"endTime\":\"16:00\"},{\"day\":\"2\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}]");
    }
}
