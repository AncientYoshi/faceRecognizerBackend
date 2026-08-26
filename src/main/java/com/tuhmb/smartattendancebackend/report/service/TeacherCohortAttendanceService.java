package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;
import com.tuhmb.smartattendancebackend.attendance.api.StudentCourseAttendanceResponse;
import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendanceSummaryResponse;
import com.tuhmb.smartattendancebackend.attendance.service.StudentAttendanceSummaryService;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.report.api.TeacherCohortAttendanceResponse;
import com.tuhmb.smartattendancebackend.report.api.TeacherStudentOverallAttendanceResponse;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TeacherCohortAttendanceService {

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_EXPORT_COURSES = 200;
    private static final long MAX_EXPORT_DATA_CELLS = 500_000L;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final StudentAttendanceSummaryService summaryService;
    private final TeacherCohortAttendanceExcelExporter excelExporter;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public TeacherCohortAttendanceService(
            TeacherRepository teacherRepository,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            StudentAttendanceSummaryService summaryService,
            TeacherCohortAttendanceExcelExporter excelExporter,
            AuditService auditService,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.summaryService = summaryService;
        this.excelExporter = excelExporter;
        this.auditService = auditService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public TeacherCohortAttendanceResponse report(
            Integer studyYear,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            String query,
            int page,
            int size,
            Jwt jwt
    ) {
        Teacher teacher = teacher(jwt);

        StudentAttendancePeriod effectivePeriod = period == null ? StudentAttendancePeriod.ALL : period;
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        Page<Student> students = studentRepository.searchDepartmentStudentsByStudentNumber(
                teacher.getDepartment().getId(),
                studyYear,
                normalizeQuery(query),
                PageRequest.of(page, size)
        );
        Map<UUID, StudentAttendanceSummaryResponse> summaries = summaryService.calculateAll(
                students.getContent(),
                effectivePeriod,
                effectiveDate
        );
        List<TeacherStudentOverallAttendanceResponse> rows = students.getContent().stream()
                .map(student -> row(student, summaries.get(student.getId())))
                .toList();
        Page<TeacherStudentOverallAttendanceResponse> responsePage = new PageImpl<>(
                rows,
                students.getPageable(),
                students.getTotalElements()
        );

        return new TeacherCohortAttendanceResponse(
                teacher.getId(),
                teacher.getDepartment().getId(),
                teacher.getDepartment().getCode(),
                teacher.getDepartment().getName(),
                studyYear,
                effectivePeriod,
                effectiveDate,
                effectivePeriod.from(effectiveDate),
                effectivePeriod.to(effectiveDate),
                Instant.now(),
                StudentAttendanceSummaryService.CALCULATION_METHOD,
                PageResponse.from(responsePage)
        );
    }

    @Transactional
    public ReportFile exportExcel(
            Integer studyYear,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            String query,
            Jwt jwt
    ) {
        TeacherCohortAttendanceReportData report = completeReport(
                studyYear,
                period,
                referenceDate,
                query,
                jwt
        );
        byte[] bytes = excelExporter.export(report);
        auditService.record(
                AuditAction.REPORT_DOWNLOADED,
                "TeacherCohortAttendanceReport",
                report.departmentId(),
                "Excel export with " + report.students().size()
                        + " students; studyYear=" + report.studyYear()
                        + "; period=" + report.period()
        );
        return new ReportFile(
                bytes,
                filename(report),
                XLSX_CONTENT_TYPE
        );
    }

    private TeacherCohortAttendanceReportData completeReport(
            Integer studyYear,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            String query,
            Jwt jwt
    ) {
        Teacher teacher = teacher(jwt);
        StudentAttendancePeriod effectivePeriod = period == null ? StudentAttendancePeriod.ALL : period;
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        Page<Student> students = studentRepository.searchDepartmentStudentsByStudentNumber(
                teacher.getDepartment().getId(),
                studyYear,
                normalizeQuery(query),
                PageRequest.of(0, MAX_EXPORT_ROWS)
        );
        if (students.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException(
                    "Overall attendance report exceeds " + MAX_EXPORT_ROWS + " students; narrow the filters"
            );
        }

        List<Course> configuredCourses = courseRepository.findByDepartmentIdAndStudyYearOrderByCode(
                teacher.getDepartment().getId(),
                studyYear
        );
        ensureCourseLimit(configuredCourses.size());
        Map<UUID, TeacherCohortAttendanceReportData.CourseColumn> courseColumns = configuredCourses.stream()
                .collect(Collectors.toMap(
                        Course::getId,
                        course -> new TeacherCohortAttendanceReportData.CourseColumn(
                                course.getId(),
                                course.getCode(),
                                course.getName(),
                                course.getSemester(),
                                course.getAcademicYear()
                        ),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<UUID, StudentAttendanceSummaryResponse> summaries = summaryService.calculateAll(
                students.getContent(),
                effectivePeriod,
                effectiveDate
        );
        summaries.values().stream()
                .flatMap(summary -> summary.courses().stream())
                .forEach(course -> courseColumns.putIfAbsent(
                        course.courseId(),
                        new TeacherCohortAttendanceReportData.CourseColumn(
                                course.courseId(),
                                course.courseCode(),
                                course.courseName(),
                                course.semester(),
                                course.academicYear()
                        )
                ));
        ensureCourseLimit(courseColumns.size());
        long dataCells = (long) students.getNumberOfElements() * (courseColumns.size() + 5L);
        if (dataCells > MAX_EXPORT_DATA_CELLS) {
            throw new IllegalArgumentException(
                    "Overall attendance report exceeds " + MAX_EXPORT_DATA_CELLS
                            + " spreadsheet cells; narrow the student filter"
            );
        }
        List<TeacherCohortAttendanceReportData.CourseColumn> courses = courseColumns.values().stream()
                .sorted(Comparator
                        .comparing(
                                TeacherCohortAttendanceReportData.CourseColumn::courseCode,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(TeacherCohortAttendanceReportData.CourseColumn::courseId))
                .toList();
        List<TeacherCohortAttendanceReportData.StudentRow> rows = students.getContent().stream()
                .map(student -> exportRow(student, summaries.get(student.getId())))
                .toList();

        return new TeacherCohortAttendanceReportData(
                teacher.getId(),
                teacher.getUser().getFirstName() + " " + teacher.getUser().getLastName(),
                teacher.getDepartment().getId(),
                teacher.getDepartment().getCode(),
                teacher.getDepartment().getName(),
                studyYear,
                effectivePeriod,
                effectiveDate,
                effectivePeriod.from(effectiveDate),
                effectivePeriod.to(effectiveDate),
                Instant.now(),
                StudentAttendanceSummaryService.CALCULATION_METHOD,
                courses,
                rows
        );
    }

    private void ensureCourseLimit(int courseCount) {
        if (courseCount > MAX_EXPORT_COURSES) {
            throw new IllegalArgumentException(
                    "Overall attendance report exceeds " + MAX_EXPORT_COURSES + " course columns"
            );
        }
    }

    private TeacherCohortAttendanceReportData.StudentRow exportRow(
            Student student,
            StudentAttendanceSummaryResponse summary
    ) {
        Map<UUID, BigDecimal> percentages = summary.courses().stream()
                .collect(Collectors.toMap(
                        StudentCourseAttendanceResponse::courseId,
                        StudentCourseAttendanceResponse::attendancePercentage,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return new TeacherCohortAttendanceReportData.StudentRow(
                student.getId(),
                student.getStudentNumber(),
                student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                percentages,
                summary.averageAttendancePercentage()
        );
    }

    private TeacherStudentOverallAttendanceResponse row(
            Student student,
            StudentAttendanceSummaryResponse summary
    ) {
        return new TeacherStudentOverallAttendanceResponse(
                student.getId(),
                student.getUser().getId(),
                student.getStudentNumber(),
                student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                student.getUser().getEmail(),
                student.getStudyYear(),
                summary.courseCount(),
                summary.averageAttendancePercentage(),
                summary.totalEligibleRollCalls(),
                summary.totalPresentRollCalls(),
                summary.totalAbsentRollCalls()
        );
    }

    private Teacher teacher(Jwt jwt) {
        Teacher teacher = teacherRepository.findByUserId(parseSubject(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile was not found"));
        if (teacher.getDepartment() == null) {
            throw new ConflictException("Teacher must be assigned to a department before viewing student attendance");
        }
        return teacher;
    }

    private String filename(TeacherCohortAttendanceReportData report) {
        return "student-overall-attendance-year-"
                + report.studyYear()
                + "-"
                + report.period().name().toLowerCase(Locale.ROOT)
                + "-"
                + java.time.ZonedDateTime.now(zoneId).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".xlsx";
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is invalid");
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
