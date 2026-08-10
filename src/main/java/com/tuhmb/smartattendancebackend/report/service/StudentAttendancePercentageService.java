package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.StudentAttendanceCountProjection;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.report.api.AttendanceReportPeriod;
import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageReportResponse;
import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentAttendancePercentageService {

    private static final int MAX_EXPORT_ROWS = 50_000;

    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentAttendancePercentagePdfExporter pdfExporter;
    private final StudentAttendancePercentageExcelExporter excelExporter;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public StudentAttendancePercentageService(
            EnrollmentRepository enrollmentRepository,
            AttendanceSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository,
            StudentAttendancePercentagePdfExporter pdfExporter,
            StudentAttendancePercentageExcelExporter excelExporter,
            AuditService auditService,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
        this.auditService = auditService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public StudentAttendancePercentageReportResponse report(
            AttendanceReportPeriod period,
            LocalDate referenceDate,
            UUID courseId,
            String query,
            int page,
            int size,
            Jwt jwt
    ) {
        UUID teacherUserId = teacherScope(jwt);
        LocalDate effectiveReferenceDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        LocalDate from = period.from(effectiveReferenceDate);
        LocalDate to = period.to(effectiveReferenceDate);

        Page<Enrollment> enrollments = findEnrollments(
                courseId,
                teacherUserId,
                query,
                PageRequest.of(page, size, reportSort())
        );
        List<StudentAttendancePercentageResponse> rows = rows(enrollments.getContent(), from, to);
        Page<StudentAttendancePercentageResponse> responsePage =
                new org.springframework.data.domain.PageImpl<>(rows, enrollments.getPageable(), enrollments.getTotalElements());
        return new StudentAttendancePercentageReportResponse(
                period,
                effectiveReferenceDate,
                from,
                to,
                Instant.now(),
                PageResponse.from(responsePage)
        );
    }

    @Transactional
    public ReportFile exportPdf(
            AttendanceReportPeriod period,
            LocalDate referenceDate,
            UUID courseId,
            String query,
            Jwt jwt
    ) {
        StudentAttendancePercentageReportData report = completeReport(period, referenceDate, courseId, query, jwt);
        auditService.record(
                AuditAction.REPORT_DOWNLOADED,
                "StudentAttendancePercentageReport",
                null,
                "PDF export with " + report.students().size() + " rows"
        );
        return new ReportFile(
                pdfExporter.export(report),
                filename(report.period(), "pdf"),
                "application/pdf"
        );
    }

    @Transactional
    public ReportFile exportExcel(
            AttendanceReportPeriod period,
            LocalDate referenceDate,
            UUID courseId,
            String query,
            Jwt jwt
    ) {
        StudentAttendancePercentageReportData report = completeReport(period, referenceDate, courseId, query, jwt);
        auditService.record(
                AuditAction.REPORT_DOWNLOADED,
                "StudentAttendancePercentageReport",
                null,
                "Excel export with " + report.students().size() + " rows"
        );
        return new ReportFile(
                excelExporter.export(report),
                filename(report.period(), "xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    private StudentAttendancePercentageReportData completeReport(
            AttendanceReportPeriod period,
            LocalDate referenceDate,
            UUID courseId,
            String query,
            Jwt jwt
    ) {
        UUID teacherUserId = teacherScope(jwt);
        LocalDate effectiveReferenceDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        LocalDate from = period.from(effectiveReferenceDate);
        LocalDate to = period.to(effectiveReferenceDate);
        Page<Enrollment> enrollments = findEnrollments(
                courseId,
                teacherUserId,
                query,
                PageRequest.of(0, MAX_EXPORT_ROWS, reportSort())
        );
        if (enrollments.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException(
                    "Student percentage report exceeds " + MAX_EXPORT_ROWS + " rows; narrow the filters"
            );
        }
        return new StudentAttendancePercentageReportData(
                period,
                effectiveReferenceDate,
                from,
                to,
                Instant.now(),
                courseId,
                query == null ? "" : query.trim(),
                rows(enrollments.getContent(), from, to)
        );
    }

    private Page<Enrollment> findEnrollments(
            UUID courseId,
            UUID teacherUserId,
            String query,
            PageRequest pageRequest
    ) {
        return enrollmentRepository.searchForStudentAttendanceReport(
                courseId,
                teacherUserId,
                normalizeQuery(query),
                pageRequest
        );
    }

    private Sort reportSort() {
        return Sort.by("course.code").ascending().and(Sort.by("student.studentNumber").ascending());
    }

    private List<StudentAttendancePercentageResponse> rows(
            List<Enrollment> enrollments,
            LocalDate from,
            LocalDate to
    ) {
        Map<UUID, Long> sessionCounts = new HashMap<>();
        Map<StudentCourseKey, Long> attendanceCounts = new HashMap<>();
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Set<UUID> courseIds = enrollments.stream()
                .map(enrollment -> enrollment.getCourse().getId())
                .collect(Collectors.toSet());
        List<AttendanceSession> sessions = sessionRepository.findEligibleReportSessions(
                List.copyOf(courseIds),
                from,
                to,
                AttendanceSessionStatus.CANCELLED,
                Instant.now()
        );
        sessions.forEach(session -> sessionCounts.merge(session.getCourse().getId(), 1L, Long::sum));

        if (!sessions.isEmpty()) {
            List<UUID> studentIds = enrollments.stream()
                    .map(enrollment -> enrollment.getStudent().getId())
                    .distinct()
                    .toList();
            List<UUID> sessionIds = sessions.stream().map(AttendanceSession::getId).toList();
            for (StudentAttendanceCountProjection count
                    : attendanceRepository.countPresentSessionsByStudentAndCourse(sessionIds, studentIds)) {
                attendanceCounts.put(
                        new StudentCourseKey(count.getStudentId(), count.getCourseId()),
                        count.getPresentSessions()
                );
            }
        }
        return enrollments.stream()
                .map(enrollment -> response(enrollment, sessionCounts, attendanceCounts))
                .toList();
    }

    private StudentAttendancePercentageResponse response(
            Enrollment enrollment,
            Map<UUID, Long> sessionCounts,
            Map<StudentCourseKey, Long> attendanceCounts
    ) {
        UUID studentId = enrollment.getStudent().getId();
        UUID courseId = enrollment.getCourse().getId();
        long totalSessions = sessionCounts.getOrDefault(courseId, 0L);
        long presentSessions = Math.min(
                attendanceCounts.getOrDefault(new StudentCourseKey(studentId, courseId), 0L),
                totalSessions
        );
        long absentSessions = totalSessions - presentSessions;
        return new StudentAttendancePercentageResponse(
                studentId,
                enrollment.getStudent().getUser().getId(),
                enrollment.getStudent().getStudentNumber(),
                enrollment.getStudent().getUser().getFirstName()
                        + " "
                        + enrollment.getStudent().getUser().getLastName(),
                courseId,
                enrollment.getCourse().getCode(),
                enrollment.getCourse().getName(),
                totalSessions,
                presentSessions,
                absentSessions,
                percentage(presentSessions, totalSessions)
        );
    }

    private BigDecimal percentage(long presentSessions, long totalSessions) {
        if (totalSessions == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(presentSessions)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSessions), 2, RoundingMode.HALF_UP);
    }

    private UUID teacherScope(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains("ADMIN")) {
            return null;
        }
        if (roles == null || !roles.contains("TEACHER")) {
            throw new AccessDeniedException("Student attendance percentages are only available to admins and teachers");
        }
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

    private String filename(AttendanceReportPeriod period, String extension) {
        return "student-attendance-"
                + period.name().toLowerCase(Locale.ROOT)
                + "-"
                + java.time.ZonedDateTime.now(zoneId).format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                )
                + "."
                + extension;
    }

    private record StudentCourseKey(UUID studentId, UUID courseId) {
    }
}
