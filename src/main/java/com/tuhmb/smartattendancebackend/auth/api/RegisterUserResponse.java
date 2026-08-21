package com.tuhmb.smartattendancebackend.auth.api;

import com.tuhmb.smartattendancebackend.user.domain.RoleName;

import java.time.Instant;
import java.util.UUID;

public record RegisterUserResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        RoleName role,
        UUID studentId,
        String studentNumber,
        Integer studyYear,
        UUID teacherId,
        String employeeNumber,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        Instant registeredAt
) {
}
