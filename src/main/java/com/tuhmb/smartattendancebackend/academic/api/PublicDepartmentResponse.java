package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Department;

import java.util.UUID;

public record PublicDepartmentResponse(
        UUID id,
        String code,
        String name
) {
    public static PublicDepartmentResponse from(Department department) {
        return new PublicDepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName()
        );
    }
}
