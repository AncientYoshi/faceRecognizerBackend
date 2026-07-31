package com.tuhmb.smartattendancebackend.dashboard.api;

import java.util.UUID;

public record DepartmentDashboardStatistic(
        UUID departmentId,
        String departmentCode,
        String departmentName,
        long students,
        long teachers,
        long courses,
        long attendanceRecords
) {
}
