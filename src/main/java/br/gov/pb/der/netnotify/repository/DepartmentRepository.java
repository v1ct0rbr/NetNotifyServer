package br.gov.pb.der.netnotify.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import br.gov.pb.der.netnotify.model.Department;
import br.gov.pb.der.netnotify.response.DepartmentResponseDto;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @Query("SELECT new br.gov.pb.der.netnotify.response.DepartmentResponseDto(d.id, d.name, d.parentDepartment.id, d.parentDepartment.name) FROM Department d left JOIN d.parentDepartment")
    public List<DepartmentResponseDto> findAllDto();

    @Query("UPDATE Department d SET d.parentDepartment = null WHERE d.parentDepartment.id = :id")
    @Modifying
    @Transactional
    public void setNullParentDepartmentById(UUID id);

    public List<Department> findByParentDepartmentId(UUID parentId);

}
