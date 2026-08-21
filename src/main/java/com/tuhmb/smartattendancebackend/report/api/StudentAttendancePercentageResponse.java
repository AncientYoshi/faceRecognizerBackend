package com.tuhmb.smartattendancebackend.report.api;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentAttendancePercentageResponse(
        UUID studentId,
        UUID studentUserId,
        String studentNumber,
        Integer studyYear,
        String studentName,
        UUID courseId,
        String courseCode,
        String courseName,
        long totalSessions,
        long presentSessions,
        long absentSessions,
        BigDecimal attendancePercentage
) {
}
