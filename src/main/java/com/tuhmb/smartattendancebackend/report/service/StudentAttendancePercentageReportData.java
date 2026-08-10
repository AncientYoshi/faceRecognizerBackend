package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.report.api.AttendanceReportPeriod;
import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentAttendancePercentageReportData(
        AttendanceReportPeriod period,
        LocalDate referenceDate,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        UUID courseId,
        String query,
        List<StudentAttendancePercentageResponse> students
) {
}
