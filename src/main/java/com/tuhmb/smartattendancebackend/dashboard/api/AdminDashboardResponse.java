package com.tuhmb.smartattendancebackend.dashboard.api;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long totalStudents,
        long totalTeachers,
        long todayAttendance,
        long expectedAttendance,
        BigDecimal attendanceRate,
        List<AttendanceSessionResponse> recentSessions,
        List<DepartmentDashboardStatistic> departmentStatistics
) {
}
