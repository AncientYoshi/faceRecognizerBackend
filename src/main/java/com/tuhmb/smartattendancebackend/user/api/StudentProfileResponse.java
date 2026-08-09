package com.tuhmb.smartattendancebackend.user.api;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Student;

import java.time.Instant;
import java.util.UUID;

public record StudentProfileResponse(
        UUID studentId,
        UUID userId,
        String studentNumber,
        String email,
        String firstName,
        String lastName,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        Instant createdAt,
        Instant updatedAt
) {
    public static StudentProfileResponse from(Student student) {
        AppUser user = student.getUser();
        Department department = student.getDepartment();
        return new StudentProfileResponse(
                student.getId(),
                user.getId(),
                student.getStudentNumber(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                department == null ? null : department.getId(),
                department == null ? null : department.getCode(),
                department == null ? null : department.getName(),
                student.getCreatedAt(),
                student.getUpdatedAt()
        );
    }
}
