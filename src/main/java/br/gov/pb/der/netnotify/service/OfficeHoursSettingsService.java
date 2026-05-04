package br.gov.pb.der.netnotify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.gov.pb.der.netnotify.config.CacheConfig;
import br.gov.pb.der.netnotify.model.OfficeHoursSettings;
import br.gov.pb.der.netnotify.repository.OfficeHoursSettingsRepository;
import br.gov.pb.der.netnotify.utils.AvailabilityWindowUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OfficeHoursSettingsService {

    private final OfficeHoursSettingsRepository officeHoursSettingsRepository;
    private final AvailabilityWindowValidator availabilityWindowValidator;

    @Value("${app.default-office-hours-windows:[{\"day\":\"1\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"2\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"3\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"4\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"},{\"day\":\"5\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]}")
    private String configuredDefaultOfficeHoursWindows;

    @Transactional
    @Cacheable(value = CacheConfig.DEFAULT_OFFICE_HOURS_WINDOW, key = "'default_office_hours_window'")
    public String getDefaultOfficeHoursWindow() {
        return findOrCreateSettings().getAvailabilityWindows();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DEFAULT_OFFICE_HOURS_WINDOW, allEntries = true)
    public String updateDefaultOfficeHoursWindow(String availabilityWindowsJson) {
        String normalizedWindows = availabilityWindowValidator.normalizeAndValidate(availabilityWindowsJson);

        OfficeHoursSettings settings = officeHoursSettingsRepository.findById(OfficeHoursSettings.SINGLETON_ID)
                .orElseGet(() -> new OfficeHoursSettings(OfficeHoursSettings.SINGLETON_ID, normalizedWindows, null));

        settings.setAvailabilityWindows(normalizedWindows);
        return officeHoursSettingsRepository.save(settings).getAvailabilityWindows();
    }

    private OfficeHoursSettings findOrCreateSettings() {
        return officeHoursSettingsRepository.findById(OfficeHoursSettings.SINGLETON_ID)
                .orElseGet(() -> officeHoursSettingsRepository.save(new OfficeHoursSettings(
                        OfficeHoursSettings.SINGLETON_ID,
                        normalizeConfiguredDefaultOfficeHours(),
                        null)));
    }

    private String normalizeConfiguredDefaultOfficeHours() {
        try {
            return AvailabilityWindowUtils.normalizeToFlatJson(configuredDefaultOfficeHoursWindows);
        } catch (Exception ex) {
            return configuredDefaultOfficeHoursWindows;
        }
    }
}
