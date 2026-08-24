package com.tuhmb.smartattendancebackend.report.api;

import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TeacherCohortAttendanceResponse(
        UUID teacherId,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        Integer studyYear,
        StudentAttendancePeriod period,
        LocalDate referenceDate,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        String calculationMethod,
        PageResponse<TeacherStudentOverallAttendanceResponse> students
) {
}
