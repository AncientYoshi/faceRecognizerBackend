package com.tuhmb.smartattendancebackend.audit.api;

import com.tuhmb.smartattendancebackend.audit.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUser() == null ? null : log.getUser().getId(),
                log.getUser() == null ? null : log.getUser().getEmail(),
                log.getAction().name(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
