package br.gov.pb.der.netnotify.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.dto.DepartmentDto;
import br.gov.pb.der.netnotify.model.Department;
import br.gov.pb.der.netnotify.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    public List<DepartmentDto> findAllDto() {
        return departmentRepository.findAllDto();
    }

    public Department findById(java.util.UUID id) {
        return departmentRepository.findById(id).orElse(null);
    }

}
