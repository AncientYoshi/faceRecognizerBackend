package com.tuhmb.smartattendancebackend.attendance.service;

import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;
import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendanceSummaryResponse;
import com.tuhmb.smartattendancebackend.attendance.api.StudentCourseAttendanceResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentAttendanceSummaryService {

    public static final String CALCULATION_METHOD = "ARITHMETIC_MEAN_OF_COURSE_PERCENTAGES";

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final ZoneId zoneId;

    public StudentAttendanceSummaryService(
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public StudentAttendanceSummaryResponse summary(
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            Jwt jwt
    ) {
        Student student = studentRepository.findByUserId(parseSubject(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found"));
        return calculate(student, period, referenceDate);
    }

    @Transactional(readOnly = true)
    public StudentAttendanceSummaryResponse calculate(
            Student student,
            StudentAttendancePeriod period,
            LocalDate referenceDate
    ) {
        return calculateAll(List.of(student), period, referenceDate).get(student.getId());
    }

    @Transactional(readOnly = true)
    public Map<UUID, StudentAttendanceSummaryResponse> calculateAll(
            List<Student> students,
            StudentAttendancePeriod period,
            LocalDate referenceDate
    ) {
        if (students.isEmpty()) {
            return Map.of();
        }
        StudentAttendancePeriod effectivePeriod = period == null ? StudentAttendancePeriod.ALL : period;
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        LocalDate from = effectivePeriod.from(effectiveDate);
        LocalDate to = effectivePeriod.to(effectiveDate);
        Instant now = Instant.now();
        Instant periodEnd = to.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant asOf = periodEnd.isBefore(now) ? periodEnd : now;

        List<UUID> studentIds = students.stream().map(Student::getId).toList();
        List<Enrollment> enrollments = enrollmentRepository.findAllByStudentIdsWithCourse(studentIds);
        Map<UUID, List<Enrollment>> enrollmentsByStudent = enrollments.stream().collect(Collectors.groupingBy(
                enrollment -> enrollment.getStudent().getId(),
                LinkedHashMap::new,
                Collectors.toList()
        ));

        List<UUID> courseIds = enrollments.stream()
                .map(enrollment -> enrollment.getCourse().getId())
                .distinct()
                .toList();
        List<AttendanceSession> sessions = courseIds.isEmpty()
                ? List.of()
                : sessionRepository.findEligibleReportSessions(
                                courseIds,
                                from,
                                to,
                                AttendanceSessionStatus.CANCELLED,
                                asOf
                        ).stream()
                        .filter(session -> session.getStatus() == AttendanceSessionStatus.CLOSED
                                || !session.getEndTime().isAfter(asOf))
                        .toList();

        Map<UUID, Long> eligibleByCourse = new HashMap<>();
        sessions.forEach(session -> eligibleByCourse.merge(
                session.getCourse().getId(),
                (long) session.getRollCallCount(),
                Long::sum
        ));
        Map<StudentCourseKey, Long> presentByStudentAndCourse = new HashMap<>();
        if (!sessions.isEmpty()) {
            attendanceRepository.countPresentSessionsByStudentAndCourse(
                    sessions.stream().map(AttendanceSession::getId).toList(),
                    studentIds
            ).forEach(count -> presentByStudentAndCourse.put(
                    new StudentCourseKey(count.getStudentId(), count.getCourseId()),
                    count.getPresentSessions()
            ));
        }

        Instant generatedAt = Instant.now();
        Map<UUID, StudentAttendanceSummaryResponse> results = new LinkedHashMap<>();
        for (Student student : students) {
            List<Enrollment> studentEnrollments = enrollmentsByStudent.getOrDefault(student.getId(), List.of());
            results.put(student.getId(), buildResponse(
                    student,
                    studentEnrollments,
                    eligibleByCourse,
                    presentByStudentAndCourse,
                    effectivePeriod,
                    effectiveDate,
                    from,
                    to,
                    generatedAt
            ));
        }
        return results;
    }

    private StudentAttendanceSummaryResponse buildResponse(
            Student student,
            List<Enrollment> enrollments,
            Map<UUID, Long> eligibleByCourse,
            Map<StudentCourseKey, Long> presentByStudentAndCourse,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            LocalDate from,
            LocalDate to,
            Instant generatedAt
    ) {
        if (enrollments.isEmpty()) {
            return empty(student, period, referenceDate, from, to, generatedAt);
        }
        List<StudentCourseAttendanceResponse> courses = enrollments.stream()
                .map(enrollment -> {
                    var course = enrollment.getCourse();
                    long eligible = eligibleByCourse.getOrDefault(course.getId(), 0L);
                    long present = Math.min(presentByStudentAndCourse.getOrDefault(
                            new StudentCourseKey(student.getId(), course.getId()),
                            0L
                    ), eligible);
                    return new StudentCourseAttendanceResponse(
                            course.getId(),
                            course.getCode(),
                            course.getName(),
                            course.getSemester(),
                            course.getAcademicYear(),
                            course.getStudyYear(),
                            eligible,
                            present,
                            eligible - present,
                            percentage(present, eligible)
                    );
                })
                .toList();

        long totalEligible = courses.stream().mapToLong(StudentCourseAttendanceResponse::eligibleRollCalls).sum();
        long totalPresent = courses.stream().mapToLong(StudentCourseAttendanceResponse::presentRollCalls).sum();
        BigDecimal percentageSum = courses.stream()
                .map(StudentCourseAttendanceResponse::attendancePercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = percentageSum.divide(
                BigDecimal.valueOf(courses.size()),
                2,
                RoundingMode.HALF_UP
        );

        return new StudentAttendanceSummaryResponse(
                student.getId(),
                student.getStudentNumber(),
                student.getStudyYear(),
                period,
                referenceDate,
                from,
                to,
                generatedAt,
                courses.size(),
                CALCULATION_METHOD,
                average,
                totalEligible,
                totalPresent,
                totalEligible - totalPresent,
                courses
        );
    }

    private StudentAttendanceSummaryResponse empty(
            Student student,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            LocalDate from,
            LocalDate to,
            Instant generatedAt
    ) {
        return new StudentAttendanceSummaryResponse(
                student.getId(),
                student.getStudentNumber(),
                student.getStudyYear(),
                period,
                referenceDate,
                from,
                to,
                generatedAt,
                0,
                CALCULATION_METHOD,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0,
                0,
                0,
                List.of()
        );
    }

    private BigDecimal percentage(long present, long eligible) {
        if (eligible == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(present)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated student was not found");
        }
    }

    private record StudentCourseKey(UUID studentId, UUID courseId) {
    }
}
