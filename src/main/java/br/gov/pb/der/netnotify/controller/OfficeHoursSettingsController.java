package br.gov.pb.der.netnotify.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.OfficeHoursSettingsDto;
import br.gov.pb.der.netnotify.service.AvailabilityWindowValidator;
import br.gov.pb.der.netnotify.service.OfficeHoursSettingsService;
import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/office-hours")
@RequiredArgsConstructor
public class OfficeHoursSettingsController {

    private final OfficeHoursSettingsService officeHoursSettingsService;
    private final AvailabilityWindowValidator availabilityWindowValidator;

    @GetMapping
    public ResponseEntity<SimpleResponseUtils<OfficeHoursSettingsDto>> getOfficeHoursSettings() {
        return ResponseEntity.ok(SimpleResponseUtils.success(
                new OfficeHoursSettingsDto(officeHoursSettingsService.getDefaultOfficeHoursWindow()),
                "Expediente padrão obtido com sucesso."));
    }

    @PutMapping
    public ResponseEntity<SimpleResponseUtils<OfficeHoursSettingsDto>> updateOfficeHoursSettings(
            @RequestBody OfficeHoursSettingsDto officeHoursSettingsDto) {
        String availabilityWindows = officeHoursSettingsDto != null ? officeHoursSettingsDto.getAvailabilityWindows() : null;

        var validationErrors = availabilityWindowValidator.validate(availabilityWindows);
        if (!validationErrors.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null, validationErrors.get(0)));
        }

        String normalizedWindows = officeHoursSettingsService.updateDefaultOfficeHoursWindow(availabilityWindows);
        return ResponseEntity.ok(SimpleResponseUtils.success(
                new OfficeHoursSettingsDto(normalizedWindows),
                "Expediente padrão atualizado com sucesso."));
    }
}
