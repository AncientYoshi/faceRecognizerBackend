package com.tuhmb.smartattendancebackend.user.api;

import com.tuhmb.smartattendancebackend.user.domain.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles,
        UUID studentId,
        String studentNumber,
        Integer studyYear,
        UUID teacherId,
        String employeeNumber,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(
            AppUser user,
            UUID studentId,
            String studentNumber,
            Integer studyYear,
            UUID teacherId,
            String employeeNumber
    ) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .sorted()
                        .toList(),
                studentId,
                studentNumber,
                studyYear,
                teacherId,
                employeeNumber,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
