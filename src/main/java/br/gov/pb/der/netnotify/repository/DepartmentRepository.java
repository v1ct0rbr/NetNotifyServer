package br.gov.pb.der.netnotify.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.gov.pb.der.netnotify.dto.DepartmentDto;
import br.gov.pb.der.netnotify.model.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @Query("SELECT new br.gov.pb.der.netnotify.dto.DepartmentDto(d.id, d.name) FROM Department d")
    public List<DepartmentDto> findAllDto();
}
