package br.gov.pb.der.netnotify.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.gov.pb.der.netnotify.model.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

}
