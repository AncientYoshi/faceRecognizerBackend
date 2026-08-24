package com.tuhmb.smartattendancebackend.attendance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentAttendanceSummaryResponse(
        UUID studentId,
        String studentNumber,
        Integer studyYear,
        StudentAttendancePeriod period,
        LocalDate referenceDate,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        int courseCount,
        String calculationMethod,
        BigDecimal averageAttendancePercentage,
        long totalEligibleRollCalls,
        long totalPresentRollCalls,
        long totalAbsentRollCalls,
        List<StudentCourseAttendanceResponse> courses
) {
}
