package com.tuhmb.smartattendancebackend.dashboard.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.dashboard.api.AdminDashboardResponse;
import com.tuhmb.smartattendancebackend.dashboard.api.DepartmentDashboardStatistic;
import com.tuhmb.smartattendancebackend.dashboard.api.TeacherCourseSummary;
import com.tuhmb.smartattendancebackend.dashboard.api.TeacherDashboardResponse;
import com.tuhmb.smartattendancebackend.dashboard.api.StudentDashboardResponse;
import com.tuhmb.smartattendancebackend.settings.domain.SystemSetting;
import com.tuhmb.smartattendancebackend.settings.repository.SystemSettingRepository;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final SystemSettingRepository settingRepository;
    private final ZoneId zoneId;

    public DashboardService(
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceRepository attendanceRepository,
            AttendanceSessionRepository sessionRepository,
            SystemSettingRepository settingRepository,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.settingRepository = settingRepository;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zoneId);
        Instant dayStart = today.atStartOfDay(zoneId).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<AttendanceSession> eligibleSessions = sessionRepository
                .findByStatusNotAndStartTimeLessThanEqual(AttendanceSessionStatus.CANCELLED, now);
        long expected = expectedAttendance(eligibleSessions);
        long recorded = attendanceRepository.sumRecordedRollCalls();

        List<DepartmentDashboardStatistic> departmentStatistics = departmentRepository
                .findAll(Sort.by("code"))
                .stream()
                .map(this::departmentStatistic)
                .toList();

        return new AdminDashboardResponse(
                studentRepository.count(),
                teacherRepository.count(),
                attendanceRepository.sumRecordedRollCallsBetween(dayStart, dayEnd),
                expected,
                rate(recorded, expected),
                sessionRepository.findTop5ByOrderByStartTimeDesc().stream()
                        .map(AttendanceSessionResponse::from)
                        .toList(),
                departmentStatistics
        );
    }

    @Transactional(readOnly = true)
    public TeacherDashboardResponse teacherDashboard(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        Teacher teacher = teacherRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile was not found"));
        List<Course> courses = courseRepository.findByTeacherIdOrderByCode(teacher.getId());
        List<TeacherCourseSummary> courseSummaries = courses.stream()
                .map(course -> new TeacherCourseSummary(
                        course.getId(),
                        course.getCode(),
                        course.getName(),
                        enrollmentRepository.countByCourseId(course.getId())
                ))
                .toList();
        List<AttendanceSession> eligibleSessions = sessionRepository
                .findByTeacherIdAndStatusNotAndStartTimeLessThanEqual(
                        teacher.getId(),
                        AttendanceSessionStatus.CANCELLED,
                        Instant.now()
                );
        long expected = expectedAttendance(eligibleSessions);
        long recorded = attendanceRepository.sumRecordedRollCallsByTeacherId(teacher.getId());
        String teacherName = teacher.getUser().getFirstName() + " " + teacher.getUser().getLastName();

        return new TeacherDashboardResponse(
                teacher.getId(),
                teacherName,
                courseSummaries,
                sessionRepository.findByTeacherIdAndSessionDateOrderByStartTimeAsc(
                                teacher.getId(),
                                LocalDate.now(zoneId)
                        ).stream()
                        .map(AttendanceSessionResponse::from)
                        .toList(),
                recorded,
                expected,
                rate(recorded, expected)
        );
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse studentDashboard(LocalDate date, Jwt jwt) {
        UUID userId = parseSubject(jwt, "Authenticated student was not found");
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found"));
        LocalDate today = LocalDate.now(zoneId);
        LocalDate effectiveDate = date == null ? today : date;
        Instant now = Instant.now();
        Instant requestedDayEnd = effectiveDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant asOf = requestedDayEnd.isBefore(now) ? requestedDayEnd : now;

        List<AttendanceSessionResponse> todaySessions = sessionRepository.findStudentSessionsForDate(
                        student.getId(),
                        effectiveDate,
                        AttendanceSessionStatus.CANCELLED
                ).stream()
                .map(AttendanceSessionResponse::from)
                .toList();

        AttendanceTotals overall = totals(student.getId(), asOf);
        YearMonth currentMonth = YearMonth.from(effectiveDate);
        AttendanceTotals currentMonthTotals = totals(
                student.getId(),
                currentMonth.atDay(1),
                currentMonth.atEndOfMonth(),
                asOf
        );
        YearMonth previousMonth = currentMonth.minusMonths(1);
        AttendanceTotals previousMonthTotals = totals(
                student.getId(),
                previousMonth.atDay(1),
                previousMonth.atEndOfMonth(),
                asOf
        );

        BigDecimal overallPercentage = averageCoursePercentage(
                student.getId(), LocalDate.of(1970, 1, 1), effectiveDate, asOf
        );
        BigDecimal currentMonthPercentage = averageCoursePercentage(
                student.getId(), currentMonth.atDay(1), currentMonth.atEndOfMonth(), asOf
        );
        BigDecimal previousMonthPercentage = averageCoursePercentage(
                student.getId(), previousMonth.atDay(1), previousMonth.atEndOfMonth(), asOf
        );
        String currentSemester = settingValue("CURRENT_SEMESTER");
        String currentAcademicYear = settingValue("CURRENT_ACADEMIC_YEAR");
        BigDecimal requiredPercentage = new BigDecimal(settingValue("ATTENDANCE_THRESHOLD"))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return new StudentDashboardResponse(
                overallPercentage,
                currentMonthPercentage,
                previousMonthPercentage,
                currentMonthPercentage.subtract(previousMonthPercentage).setScale(2, RoundingMode.HALF_UP),
                overall.present(),
                overall.eligible(),
                overall.present(),
                Math.max(0, overall.eligible() - overall.present()),
                todaySessions.size(),
                enrollmentRepository.countCurrentTermCourses(
                        student.getId(),
                        currentSemester,
                        currentAcademicYear
                ),
                requiredPercentage,
                todaySessions
        );
    }

    private DepartmentDashboardStatistic departmentStatistic(Department department) {
        return new DepartmentDashboardStatistic(
                department.getId(),
                department.getCode(),
                department.getName(),
                studentRepository.countByDepartmentId(department.getId()),
                teacherRepository.countByDepartmentId(department.getId()),
                courseRepository.countByDepartmentId(department.getId()),
                attendanceRepository.sumRecordedRollCallsByDepartmentId(department.getId())
        );
    }

    private long expectedAttendance(List<AttendanceSession> sessions) {
        return sessions.stream()
                .mapToLong(session -> enrollmentRepository.countByCourseId(session.getCourse().getId())
                        * session.getRollCallCount())
                .sum();
    }

    private BigDecimal rate(long recorded, long expected) {
        if (expected == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(recorded)
                .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
    }

    private AttendanceTotals totals(UUID studentId, Instant asOf) {
        long eligible = sessionRepository.countStudentEligibleSessions(
                studentId,
                AttendanceSessionStatus.CANCELLED,
                AttendanceSessionStatus.CLOSED,
                asOf
        );
        long present = attendanceRepository.countStudentPresentEligibleSessions(
                studentId,
                AttendanceStatus.PRESENT,
                AttendanceSessionStatus.CANCELLED,
                AttendanceSessionStatus.CLOSED,
                asOf
        );
        return new AttendanceTotals(eligible, Math.min(present, eligible));
    }

    private AttendanceTotals totals(UUID studentId, LocalDate from, LocalDate to, Instant asOf) {
        long eligible = sessionRepository.countStudentEligibleSessionsBetween(
                studentId,
                from,
                to,
                AttendanceSessionStatus.CANCELLED,
                AttendanceSessionStatus.CLOSED,
                asOf
        );
        long present = attendanceRepository.countStudentPresentEligibleSessionsBetween(
                studentId,
                from,
                to,
                AttendanceStatus.PRESENT,
                AttendanceSessionStatus.CANCELLED,
                AttendanceSessionStatus.CLOSED,
                asOf
        );
        return new AttendanceTotals(eligible, Math.min(present, eligible));
    }

    private BigDecimal percentage(long present, long eligible) {
        if (eligible == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(present)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageCoursePercentage(
            UUID studentId,
            LocalDate from,
            LocalDate to,
            Instant asOf
    ) {
        List<com.tuhmb.smartattendancebackend.academic.domain.Enrollment> enrollments =
                enrollmentRepository.findAllByStudentIdOrderByCourseCode(studentId);
        if (enrollments.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<UUID> courseIds = enrollments.stream()
                .map(enrollment -> enrollment.getCourse().getId())
                .toList();
        List<AttendanceSession> sessions = sessionRepository.findEligibleReportSessions(
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
        Map<UUID, Long> presentByCourse = new HashMap<>();
        if (!sessions.isEmpty()) {
            attendanceRepository.countPresentSessionsByStudentAndCourse(
                    sessions.stream().map(AttendanceSession::getId).toList(),
                    List.of(studentId)
            ).forEach(count -> presentByCourse.put(count.getCourseId(), count.getPresentSessions()));
        }
        BigDecimal sum = enrollments.stream()
                .map(enrollment -> {
                    UUID courseId = enrollment.getCourse().getId();
                    long eligible = eligibleByCourse.getOrDefault(courseId, 0L);
                    long present = Math.min(presentByCourse.getOrDefault(courseId, 0L), eligible);
                    return percentage(present, eligible);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(
                BigDecimal.valueOf(enrollments.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private String settingValue(String key) {
        SystemSetting setting = settingRepository.findByKeyIgnoreCase(key)
                .orElseThrow(() -> new ResourceNotFoundException("System setting " + key + " was not found"));
        return setting.getValue().trim();
    }

    private UUID parseSubject(Jwt jwt) {
        return parseSubject(jwt, "Authenticated teacher was not found");
    }

    private UUID parseSubject(Jwt jwt, String message) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException(message);
        }
    }

    private record AttendanceTotals(long eligible, long present) {
    }
}
