package com.tuhmb.smartattendancebackend.attendance.api;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentCourseAttendanceResponse(
        UUID courseId,
        String courseCode,
        String courseName,
        String semester,
        String academicYear,
        Integer studyYear,
        long eligibleRollCalls,
        long presentRollCalls,
        long absentRollCalls,
        BigDecimal attendancePercentage
) {
}
