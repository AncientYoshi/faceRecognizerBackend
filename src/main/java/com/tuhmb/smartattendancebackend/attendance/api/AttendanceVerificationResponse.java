package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;

import java.time.Instant;
import java.util.UUID;

public record AttendanceVerificationResponse(
        boolean matched,
        double similarity,
        UUID attendanceId,
        String status,
        Instant verifiedAt,
        String message
) {
    public static AttendanceVerificationResponse notMatched(double similarity) {
        return new AttendanceVerificationResponse(
                false,
                similarity,
                null,
                null,
                null,
                "Face did not match the registered student"
        );
    }

    public static AttendanceVerificationResponse recorded(Attendance attendance) {
        return new AttendanceVerificationResponse(
                true,
                attendance.getSimilarityScore().doubleValue(),
                attendance.getId(),
                attendance.getStatus().name(),
                attendance.getVerifiedAt(),
                "Attendance recorded successfully"
        );
    }
}
