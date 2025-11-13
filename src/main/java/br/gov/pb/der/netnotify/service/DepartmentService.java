package br.gov.pb.der.netnotify.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.dto.DepartmentDto;
import br.gov.pb.der.netnotify.model.Department;
import br.gov.pb.der.netnotify.repository.DepartmentRepository;
import br.gov.pb.der.netnotify.response.DepartmentResponseDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    public List<DepartmentResponseDto> findAllDto() {
        return departmentRepository.findAllDto();
    }

    public Department findById(java.util.UUID id) {
        return departmentRepository.findById(id).orElse(null);
    }

    public Department saveFromDto(DepartmentDto dto) {
        Department department = new Department();
        department.setName(dto.getName());
        if (dto.getParentDepartmentId() != null) {
            Department parent = departmentRepository.findById(dto.getParentDepartmentId()).orElse(null);
            department.setParentDepartment(parent);
        }
        departmentRepository.save(department);
        return department;

    }

    public void deleteById(java.util.UUID id) {
        departmentRepository.deleteById(id);
    }

}
