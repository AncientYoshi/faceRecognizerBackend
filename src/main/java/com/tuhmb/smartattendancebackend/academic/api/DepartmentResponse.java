package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Department;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String code,
        String name,
        String description,
        long studentCount,
        long teacherCount,
        long courseCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static DepartmentResponse from(
            Department department,
            long studentCount,
            long teacherCount,
            long courseCount
    ) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getDescription(),
                studentCount,
                teacherCount,
                courseCount,
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }
}
