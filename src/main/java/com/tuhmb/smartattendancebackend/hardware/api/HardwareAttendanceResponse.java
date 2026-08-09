package com.tuhmb.smartattendancebackend.hardware.api;

import java.util.UUID;

public record HardwareAttendanceResponse(
        boolean verified,
        String reason,
        UUID studentId,
        String rollNumber,
        boolean attendanceRecorded,
        UUID attendanceId,
        double similarity
) {
    public static HardwareAttendanceResponse failed(String reason, double similarity) {
        return new HardwareAttendanceResponse(
                false,
                reason,
                null,
                null,
                false,
                null,
                similarity
        );
    }
}
