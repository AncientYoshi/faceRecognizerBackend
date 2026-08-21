package com.tuhmb.smartattendancebackend.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CourseRollCallRegister(
        UUID courseId,
        String courseCode,
        String courseName,
        String academicYear,
        String semester,
        Integer studyYear,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        List<RollCallColumn> columns,
        List<StudentRow> students
) {
    public record RollCallColumn(
            UUID sessionId,
            LocalDate sessionDate,
            Instant startTime,
            int callNumber
    ) {
    }

    public record StudentRow(
            UUID studentId,
            String studentNumber,
            String studentName,
            Integer studyYear,
            List<Boolean> presence,
            long totalRollCalls,
            long presentRollCalls,
            long absentRollCalls,
            BigDecimal attendancePercentage
    ) {
    }
}
