package com.tuhmb.smartattendancebackend.auth.api;

import com.tuhmb.smartattendancebackend.user.domain.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles,
        Instant createdAt
) {
    public static MeResponse from(AppUser user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                roles,
                user.getCreatedAt()
        );
    }
}
