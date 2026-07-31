package com.tuhmb.smartattendancebackend.user.api;

import com.tuhmb.smartattendancebackend.user.domain.Role;

import java.util.UUID;

public record RoleResponse(UUID id, String name) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName().name());
    }
}
