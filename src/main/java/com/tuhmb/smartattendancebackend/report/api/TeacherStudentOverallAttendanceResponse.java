package com.tuhmb.smartattendancebackend.report.api;

import java.math.BigDecimal;
import java.util.UUID;

public record TeacherStudentOverallAttendanceResponse(
        UUID studentId,
        UUID studentUserId,
        String studentNumber,
        String studentName,
        String email,
        Integer studyYear,
        int courseCount,
        BigDecimal overallAttendancePercentage,
        long totalEligibleRollCalls,
        long totalPresentRollCalls,
        long totalAbsentRollCalls
) {
}
