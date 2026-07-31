package com.tuhmb.smartattendancebackend.face.api;

import com.tuhmb.smartattendancebackend.face.domain.FaceRegistration;

import java.time.Instant;
import java.util.UUID;

public record FaceRegistrationResponse(
        UUID id,
        UUID studentId,
        String studentNumber,
        String embeddingId,
        Instant registeredAt,
        Instant updatedAt
) {
    public static FaceRegistrationResponse from(FaceRegistration registration) {
        return new FaceRegistrationResponse(
                registration.getId(),
                registration.getStudent().getId(),
                registration.getStudent().getStudentNumber(),
                registration.getEmbeddingId(),
                registration.getCreatedAt(),
                registration.getUpdatedAt()
        );
    }
}
