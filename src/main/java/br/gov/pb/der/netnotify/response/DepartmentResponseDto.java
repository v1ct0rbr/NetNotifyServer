package br.gov.pb.der.netnotify.response;

import java.io.Serializable;
import java.util.UUID;

import lombok.Data;

@Data
public class DepartmentResponseDto implements Serializable {

    private UUID id;
    private String name;
    private DepartmentInfo parentDepartment;

    public DepartmentResponseDto() {

    }

    public DepartmentResponseDto(UUID id, String name, UUID parentDepartmentId, String parentDepartmentName) {
        this.id = id;
        this.name = name;
        if (parentDepartmentId != null && parentDepartmentName != null) {
            this.parentDepartment = new DepartmentInfo(parentDepartmentId, parentDepartmentName);
        }
    }

    @lombok.AllArgsConstructor
    @lombok.Data
    public static class DepartmentInfo implements java.io.Serializable {

        private java.util.UUID id;
        private String name;
    }
}
