package com.tuhmb.smartattendancebackend.report.api;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;

public record AttendanceReportResponse(
        long totalRecords,
        long expectedAttendance,
        BigDecimal attendanceRate,
        Instant generatedAt,
        PageResponse<AttendanceResponse> records
) {
}
