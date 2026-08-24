package com.tuhmb.smartattendancebackend.hardware.api;

import java.util.UUID;

public record HardwareAttendanceResponse(
        boolean verified,
        String reason,
        UUID sessionId,
        String courseCode,
        int rollCallCount,
        UUID studentId,
        String rollNumber,
        boolean attendanceRecorded,
        UUID attendanceId,
        double similarity
) {
}
