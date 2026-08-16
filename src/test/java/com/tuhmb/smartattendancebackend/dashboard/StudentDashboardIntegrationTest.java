package com.tuhmb.smartattendancebackend.dashboard;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.audit.repository.AuditLogRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentDashboardIntegrationTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Yangon");

    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired FaceRegistrationRepository faceRegistrationRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        departmentRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        teacherRepository.deleteAll();
        studentRepository.deleteAll();
        userRepository.deleteAll();

        today = LocalDate.now(ZONE_ID);
        transactionTemplate.executeWithoutResult(status -> createDashboardData());
    }

    @Test
    void studentDashboardUsesCompleteAttendanceHistoryAndConfiguredCurrentTerm() throws Exception {
        String studentToken = login("dashboard.student@example.com", "student-password");

        mockMvc.perform(get("/dashboard/student")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallAttendancePercentage").value(66.67))
                .andExpect(jsonPath("$.currentMonthPercentage").value(50.0))
                .andExpect(jsonPath("$.previousMonthPercentage").value(100.0))
                .andExpect(jsonPath("$.monthlyChange").value(-50.0))
                .andExpect(jsonPath("$.classesAttended").value(2))
                .andExpect(jsonPath("$.eligibleSessions").value(3))
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.todaySessionCount").value(3))
                .andExpect(jsonPath("$.activeCourseCount").value(1))
                .andExpect(jsonPath("$.requiredAttendancePercentage").value(75.0))
                .andExpect(jsonPath("$.todaySessions.length()").value(3))
                .andExpect(jsonPath("$.todaySessions[0].courseCode").value("DASH-101"))
                .andExpect(jsonPath("$.lateCount").doesNotExist());
    }

    @Test
    void studentDashboardRejectsTeacherTokens() throws Exception {
        String teacherToken = login("dashboard.teacher@example.com", "teacher-password");

        mockMvc.perform(get("/dashboard/student")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
    }

    private void createDashboardData() {
        Role teacherRole = roleRepository.findByName(RoleName.TEACHER).orElseThrow();
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
        AppUser teacherUser = userRepository.save(new AppUser(
                "dashboard.teacher@example.com",
                passwordEncoder.encode("teacher-password"),
                "Dashboard",
                "Teacher",
                Set.of(teacherRole)
        ));
        AppUser studentUser = userRepository.save(new AppUser(
                "dashboard.student@example.com",
                passwordEncoder.encode("student-password"),
                "Dashboard",
                "Student",
                Set.of(studentRole)
        ));
        Department department = departmentRepository.save(
                new Department("DASH", "Dashboard Department", null)
        );
        Teacher teacher = new Teacher(teacherUser, "DASH-T-001");
        teacher.assignDepartment(department);
        teacherRepository.save(teacher);
        Student student = new Student(studentUser, "DASH-S-001");
        student.assignDepartment(department);
        studentRepository.save(student);
        Course course = courseRepository.save(new Course(
                "DASH-101",
                "Dashboard Calculations",
                "FIRST",
                "2026-2027",
                department,
                teacher
        ));
        enrollmentRepository.save(new Enrollment(student, course));

        Instant now = Instant.now();
        AttendanceSession activeSession = sessionRepository.save(new AttendanceSession(
                course,
                teacher,
                today,
                now.minusSeconds(60),
                now.plusSeconds(3600)
        ));
        activeSession.start();

        AttendanceSession currentPresent = closedSession(
                course,
                teacher,
                today,
                now.minusSeconds(10_800),
                now.minusSeconds(7_200)
        );
        attendanceRepository.save(new Attendance(
                student,
                course,
                currentPresent,
                new BigDecimal("0.95000"),
                now.minusSeconds(7_100)
        ));
        closedSession(
                course,
                teacher,
                today,
                now.minusSeconds(7_000),
                now.minusSeconds(3_600)
        );

        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        LocalDate previousDate = previousMonth.atDay(Math.min(15, previousMonth.lengthOfMonth()));
        Instant previousStart = previousDate.atTime(9, 0).atZone(ZONE_ID).toInstant();
        AttendanceSession previousPresent = closedSession(
                course,
                teacher,
                previousDate,
                previousStart,
                previousStart.plusSeconds(3_600)
        );
        attendanceRepository.save(new Attendance(
                student,
                course,
                previousPresent,
                new BigDecimal("0.93000"),
                previousStart.plusSeconds(1_800)
        ));

        AttendanceSession cancelled = sessionRepository.save(new AttendanceSession(
                course,
                teacher,
                today,
                now.plusSeconds(7_200),
                now.plusSeconds(10_800)
        ));
        cancelled.cancel();
    }

    private AttendanceSession closedSession(
            Course course,
            Teacher teacher,
            LocalDate date,
            Instant start,
            Instant end
    ) {
        AttendanceSession session = sessionRepository.save(
                new AttendanceSession(course, teacher, date, start, end)
        );
        session.start();
        session.close();
        return session;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
