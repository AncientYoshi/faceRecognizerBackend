package com.tuhmb.smartattendancebackend.hardware.api;

import java.time.Instant;

public record PublicPhotoUploadTestResponse(
        String message,
        long receivedBytes,
        String sha256,
        String savedFile,
        Instant receivedAt
) {
}
