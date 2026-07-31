package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AttendanceReportData(
        long totalRecords,
        long expectedAttendance,
        BigDecimal attendanceRate,
        Instant generatedAt,
        AttendanceReportFilter filter,
        List<AttendanceResponse> records
) {
}
