package com.tuhmb.smartattendancebackend.face.client;

import java.util.UUID;

public record IdentifyFaceAiResponse(
        boolean matched,
        UUID studentId,
        double similarity,
        boolean livenessPassed,
        String reason
) {
}
