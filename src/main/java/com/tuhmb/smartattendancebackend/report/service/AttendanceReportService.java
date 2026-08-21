package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.report.api.AttendanceReportResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceReportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_EXPORT_RECORDS = 50_000;

    private final AttendanceRepository attendanceRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PdfAttendanceReportExporter pdfExporter;
    private final ExcelAttendanceReportExporter excelExporter;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public AttendanceReportService(
            AttendanceRepository attendanceRepository,
            AttendanceSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            PdfAttendanceReportExporter pdfExporter,
            ExcelAttendanceReportExporter excelExporter,
            AuditService auditService,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
        this.auditService = auditService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public AttendanceReportResponse search(
            AttendanceReportFilter filter,
            int page,
            int size,
            Jwt jwt
    ) {
        Specification<Attendance> specification = attendanceSpecification(filter, jwt);
        Page<Attendance> result = attendanceRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "verifiedAt"))
        );
        long expected = expectedAttendance(filter, jwt);
        long recordedRollCalls = result.getContent().stream()
                .mapToLong(attendance -> attendance.getSession().getRollCallCount())
                .sum();
        if (page > 0 || result.getTotalPages() > 1) {
            recordedRollCalls = attendanceRepository.findAll(specification).stream()
                    .mapToLong(attendance -> attendance.getSession().getRollCallCount())
                    .sum();
        }
        return new AttendanceReportResponse(
                recordedRollCalls,
                expected,
                rate(recordedRollCalls, expected),
                Instant.now(),
                PageResponse.from(result.map(AttendanceResponse::from))
        );
    }

    @Transactional
    public ReportFile exportPdf(AttendanceReportFilter filter, Jwt jwt) {
        AttendanceReportData report = completeReport(filter, jwt);
        byte[] bytes = pdfExporter.export(report);
        auditService.record(
                AuditAction.REPORT_DOWNLOADED,
                "AttendanceReport",
                null,
                "PDF export with " + report.totalRecords() + " records"
        );
        return new ReportFile(bytes, filename("pdf"), "application/pdf");
    }

    @Transactional
    public ReportFile exportExcel(AttendanceReportFilter filter, Jwt jwt) {
        AttendanceReportData report = completeReport(filter, jwt);
        byte[] bytes = excelExporter.export(report);
        auditService.record(
                AuditAction.REPORT_DOWNLOADED,
                "AttendanceReport",
                null,
                "Excel export with " + report.totalRecords() + " records"
        );
        return new ReportFile(
                bytes,
                filename("xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    private AttendanceReportData completeReport(AttendanceReportFilter filter, Jwt jwt) {
        List<Attendance> attendance = attendanceRepository.findAll(
                attendanceSpecification(filter, jwt),
                Sort.by(Sort.Direction.ASC, "verifiedAt")
        );
        if (attendance.size() > MAX_EXPORT_RECORDS) {
            throw new IllegalArgumentException(
                    "Report exceeds " + MAX_EXPORT_RECORDS + " records; narrow the filters before exporting"
            );
        }
        long expected = expectedAttendance(filter, jwt);
        long recordedRollCalls = attendance.stream()
                .mapToLong(record -> record.getSession().getRollCallCount())
                .sum();
        return new AttendanceReportData(
                recordedRollCalls,
                expected,
                rate(recordedRollCalls, expected),
                Instant.now(),
                filter,
                attendance.stream().map(AttendanceResponse::from).toList()
        );
    }

    private Specification<Attendance> attendanceSpecification(AttendanceReportFilter filter, Jwt jwt) {
        UUID userId = subject(jwt);
        boolean admin = hasRole(jwt, "ADMIN");
        if (!admin && !hasRole(jwt, "TEACHER")) {
            throw new AccessDeniedException("Attendance reports are only available to admins and teachers");
        }
        Instant fromInstant = filter.from() == null
                ? null
                : filter.from().atStartOfDay(zoneId).toInstant();
        Instant toExclusive = filter.to() == null
                ? null
                : filter.to().plusDays(1).atStartOfDay(zoneId).toInstant();

        return (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.courseId() != null) {
                predicates.add(builder.equal(root.get("course").get("id"), filter.courseId()));
            }
            if (filter.studentId() != null) {
                predicates.add(builder.equal(root.get("student").get("id"), filter.studentId()));
            }
            if (filter.departmentId() != null) {
                predicates.add(builder.equal(
                        root.get("course").get("department").get("id"),
                        filter.departmentId()
                ));
            }
            if (fromInstant != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("verifiedAt"), fromInstant));
            }
            if (toExclusive != null) {
                predicates.add(builder.lessThan(root.get("verifiedAt"), toExclusive));
            }
            if (!admin) {
                predicates.add(builder.equal(
                        root.get("course").get("teacher").get("user").get("id"),
                        userId
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private long expectedAttendance(AttendanceReportFilter filter, Jwt jwt) {
        UUID userId = subject(jwt);
        boolean admin = hasRole(jwt, "ADMIN");
        Specification<AttendanceSession> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.notEqual(root.get("status"), AttendanceSessionStatus.CANCELLED));
            predicates.add(builder.lessThanOrEqualTo(root.get("startTime"), Instant.now()));
            if (filter.courseId() != null) {
                predicates.add(builder.equal(root.get("course").get("id"), filter.courseId()));
            }
            if (filter.departmentId() != null) {
                predicates.add(builder.equal(
                        root.get("course").get("department").get("id"),
                        filter.departmentId()
                ));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("sessionDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("sessionDate"), filter.to()));
            }
            if (!admin) {
                predicates.add(builder.equal(
                        root.get("course").get("teacher").get("user").get("id"),
                        userId
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        return sessionRepository.findAll(specification).stream()
                .mapToLong(session -> filter.studentId() == null
                        ? enrollmentRepository.countByCourseId(session.getCourse().getId())
                                * session.getRollCallCount()
                        : enrollmentRepository.existsByStudentIdAndCourseId(
                                filter.studentId(),
                                session.getCourse().getId()
                        ) ? session.getRollCallCount() : 0)
                .sum();
    }

    private BigDecimal rate(long recorded, long expected) {
        if (expected == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(recorded)
                .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
    }

    private String filename(String extension) {
        return "attendance-report-" + FILE_DATE.format(java.time.ZonedDateTime.now(zoneId)) + "." + extension;
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is invalid");
        }
    }
}
