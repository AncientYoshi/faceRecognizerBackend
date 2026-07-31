package com.tuhmb.smartattendancebackend.dashboard.api;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TeacherDashboardResponse(
        UUID teacherId,
        String teacherName,
        List<TeacherCourseSummary> myCourses,
        List<AttendanceSessionResponse> todaySessions,
        long attendanceRecords,
        long expectedAttendance,
        BigDecimal attendanceRate
) {
}
