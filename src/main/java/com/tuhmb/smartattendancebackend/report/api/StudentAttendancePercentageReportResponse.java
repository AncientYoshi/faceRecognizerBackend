package com.tuhmb.smartattendancebackend.report.api;

import com.tuhmb.smartattendancebackend.common.api.PageResponse;

import java.time.Instant;
import java.time.LocalDate;

public record StudentAttendancePercentageReportResponse(
        AttendanceReportPeriod period,
        LocalDate referenceDate,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        PageResponse<StudentAttendancePercentageResponse> students
) {
}
