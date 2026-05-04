package br.gov.pb.der.netnotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.gov.pb.der.netnotify.model.OfficeHoursSettings;
import br.gov.pb.der.netnotify.repository.OfficeHoursSettingsRepository;

class OfficeHoursSettingsServiceTest {

    @Test
    void createsDatabaseSettingFromConfiguredFallbackWhenMissing() {
        OfficeHoursSettingsRepository repository = org.mockito.Mockito.mock(OfficeHoursSettingsRepository.class);
        AvailabilityWindowValidator validator = new AvailabilityWindowValidator();
        OfficeHoursSettingsService service = new OfficeHoursSettingsService(repository, validator);
        ReflectionTestUtils.setField(service, "configuredDefaultOfficeHoursWindows",
                "[{\"day\":\"1\",\"intervals\":[{\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]}]");

        OfficeHoursSettings savedSettings = new OfficeHoursSettings(
                OfficeHoursSettings.SINGLETON_ID,
                "[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]",
                LocalDateTime.now());

        when(repository.findById(OfficeHoursSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(OfficeHoursSettings.class))).thenReturn(savedSettings);

        String availabilityWindows = service.getDefaultOfficeHoursWindow();

        assertThat(availabilityWindows).isEqualTo("[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]");
        verify(repository).save(any(OfficeHoursSettings.class));
    }

    @Test
    void normalizesPayloadBeforePersistingUpdate() {
        OfficeHoursSettingsRepository repository = org.mockito.Mockito.mock(OfficeHoursSettingsRepository.class);
        AvailabilityWindowValidator validator = new AvailabilityWindowValidator();
        OfficeHoursSettingsService service = new OfficeHoursSettingsService(repository, validator);

        OfficeHoursSettings existingSettings = new OfficeHoursSettings(
                OfficeHoursSettings.SINGLETON_ID,
                "[]",
                LocalDateTime.now());

        when(repository.findById(OfficeHoursSettings.SINGLETON_ID)).thenReturn(Optional.of(existingSettings));
        when(repository.save(any(OfficeHoursSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updatedWindows = service.updateDefaultOfficeHoursWindow(
                "[{\"day\":\"1\",\"intervals\":[{\"startTime\":\"08:00\",\"endTime\":\"12:00\"},{\"startTime\":\"13:00\",\"endTime\":\"17:00\"}]}]");

        assertThat(updatedWindows).isEqualTo(
                "[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"12:00\"},{\"day\":\"1\",\"startTime\":\"13:00\",\"endTime\":\"17:00\"}]");
        assertThat(existingSettings.getAvailabilityWindows()).isEqualTo(updatedWindows);
    }
}
