package com.tuhmb.smartattendancebackend.academic.api;

import java.util.UUID;

public record ProfileAssignmentResponse(
        UUID profileId,
        UUID userId,
        String fullName,
        String referenceNumber,
        UUID departmentId,
        String departmentName
) {
}