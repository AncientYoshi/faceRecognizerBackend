package com.tuhmb.smartattendancebackend.dashboard.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.dashboard.api.AdminDashboardResponse;
import com.tuhmb.smartattendancebackend.dashboard.api.DepartmentDashboardStatistic;
import com.tuhmb.smartattendancebackend.dashboard.api.TeacherCourseSummary;
import com.tuhmb.smartattendancebackend.dashboard.api.TeacherDashboardResponse;
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
import java.time.ZoneId;
import java.util.List;
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
    private final ZoneId zoneId;

    public DashboardService(
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceRepository attendanceRepository,
            AttendanceSessionRepository sessionRepository,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
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
        long recorded = attendanceRepository.count();

        List<DepartmentDashboardStatistic> departmentStatistics = departmentRepository
                .findAll(Sort.by("code"))
                .stream()
                .map(this::departmentStatistic)
                .toList();

        return new AdminDashboardResponse(
                studentRepository.count(),
                teacherRepository.count(),
                attendanceRepository.countByVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(dayStart, dayEnd),
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
        long recorded = attendanceRepository.countByCourseTeacherId(teacher.getId());
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

    private DepartmentDashboardStatistic departmentStatistic(Department department) {
        return new DepartmentDashboardStatistic(
                department.getId(),
                department.getCode(),
                department.getName(),
                studentRepository.countByDepartmentId(department.getId()),
                teacherRepository.countByDepartmentId(department.getId()),
                courseRepository.countByDepartmentId(department.getId()),
                attendanceRepository.countByCourseDepartmentId(department.getId())
        );
    }

    private long expectedAttendance(List<AttendanceSession> sessions) {
        return sessions.stream()
                .mapToLong(session -> enrollmentRepository.countByCourseId(session.getCourse().getId()))
                .sum();
    }

    private BigDecimal rate(long recorded, long expected) {
        if (expected == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(recorded)
                .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated teacher was not found");
        }
    }
}
