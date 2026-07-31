package com.tuhmb.smartattendancebackend.report.service;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceReportFilter(
        UUID courseId,
        UUID studentId,
        UUID departmentId,
        LocalDate from,
        LocalDate to
) {
    public AttendanceReportFilter {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("'to' date must be on or after 'from' date");
        }
    }
}
