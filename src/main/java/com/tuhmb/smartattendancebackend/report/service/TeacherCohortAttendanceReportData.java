package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TeacherCohortAttendanceReportData(
        UUID teacherId,
        String teacherName,
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
        List<CourseColumn> courses,
        List<StudentRow> students
) {

    public TeacherCohortAttendanceReportData {
        courses = List.copyOf(courses);
        students = List.copyOf(students);
    }

    public record CourseColumn(
            UUID courseId,
            String courseCode,
            String courseName,
            String semester,
            String academicYear
    ) {
    }

    public record StudentRow(
            UUID studentId,
            String studentNumber,
            String studentName,
            Map<UUID, BigDecimal> coursePercentages,
            BigDecimal overallAttendancePercentage
    ) {

        public StudentRow {
            coursePercentages = Map.copyOf(coursePercentages);
        }
    }
}
