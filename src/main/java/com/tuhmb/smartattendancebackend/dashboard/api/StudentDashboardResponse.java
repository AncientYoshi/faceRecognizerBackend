package com.tuhmb.smartattendancebackend.dashboard.api;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;

import java.math.BigDecimal;
import java.util.List;

public record StudentDashboardResponse(
        BigDecimal overallAttendancePercentage,
        BigDecimal currentMonthPercentage,
        BigDecimal previousMonthPercentage,
        BigDecimal monthlyChange,
        long classesAttended,
        long eligibleSessions,
        long presentCount,
        long absentCount,
        long todaySessionCount,
        long activeCourseCount,
        BigDecimal requiredAttendancePercentage,
        List<AttendanceSessionResponse> todaySessions
) {
}
