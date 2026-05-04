package br.gov.pb.der.netnotify.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.gov.pb.der.netnotify.model.OfficeHoursSettings;

public interface OfficeHoursSettingsRepository extends JpaRepository<OfficeHoursSettings, Integer> {
}
