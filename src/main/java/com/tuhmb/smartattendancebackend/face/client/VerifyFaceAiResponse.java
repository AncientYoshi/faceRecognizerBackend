package com.tuhmb.smartattendancebackend.face.client;

public record VerifyFaceAiResponse(boolean matched, double similarity) {
}
