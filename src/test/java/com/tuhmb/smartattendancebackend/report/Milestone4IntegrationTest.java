package com.tuhmb.smartattendancebackend.report;

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
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Milestone4IntegrationTest {

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

    private UUID courseId;
    private UUID studentId;

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
        userRepository.deleteAll();

        transactionTemplate.executeWithoutResult(status -> {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
            Role teacherRole = roleRepository.findByName(RoleName.TEACHER).orElseThrow();
            Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
            userRepository.save(new AppUser(
                    "admin4@example.com",
                    passwordEncoder.encode("admin-password"),
                    "System",
                    "Admin",
                    Set.of(adminRole)
            ));
            AppUser teacherUser = userRepository.save(new AppUser(
                    "teacher4@example.com",
                    passwordEncoder.encode("teacher-password"),
                    "Grace",
                    "Teacher",
                    Set.of(teacherRole)
            ));
            AppUser studentUser = userRepository.save(new AppUser(
                    "student4@example.com",
                    passwordEncoder.encode("student-password"),
                    "Alice",
                    "Student",
                    Set.of(studentRole)
            ));
            Department department = departmentRepository.save(
                    new Department("M4-CSE", "Milestone Four Computer Science", null)
            );
            Teacher teacher = new Teacher(teacherUser, "M4-TCH-001");
            teacher.assignDepartment(department);
            teacherRepository.save(teacher);
            Student student = new Student(studentUser, "M4-STU-001");
            student.assignDepartment(department);
            studentRepository.save(student);
            Course course = courseRepository.save(new Course(
                    "M4-CSE-101",
                    "Administration and Reporting",
                    "FIRST",
                    "2026-2027",
                    department,
                    teacher
            ));
            enrollmentRepository.save(new Enrollment(student, course));
            Instant now = Instant.now();
            AttendanceSession session = new AttendanceSession(
                    course,
                    teacher,
                    LocalDate.now(ZoneId.of("Asia/Yangon")),
                    now.minusSeconds(120),
                    now.plusSeconds(3600)
            );
            session.start();
            sessionRepository.save(session);
            attendanceRepository.save(new Attendance(
                    student,
                    course,
                    session,
                    new BigDecimal("0.92345"),
                    now
            ));
            courseId = course.getId();
            studentId = student.getId();
        });
    }

    @Test
    void dashboardsReportsSettingsAndAuditAreAvailable() throws Exception {
        String adminToken = login("admin4@example.com", "admin-password");
        String teacherToken = login("teacher4@example.com", "teacher-password");

        mockMvc.perform(get("/dashboard/admin").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(1))
                .andExpect(jsonPath("$.todayAttendance").value(1))
                .andExpect(jsonPath("$.attendanceRate").value(1.0));

        mockMvc.perform(get("/dashboard/teacher").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myCourses[0].courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.todaySessions[0].courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.attendanceRate").value(1.0));

        mockMvc.perform(get("/reports/attendance")
                        .param("courseId", courseId.toString())
                        .param("studentId", studentId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(1))
                .andExpect(jsonPath("$.records.totalElements").value(1));

        mockMvc.perform(get("/reports/attendance/export/pdf")
                        .param("courseId", courseId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attendance-report-")))
                .andExpect(content().contentType("application/pdf"));

        mockMvc.perform(get("/reports/attendance/export/excel")
                        .param("courseId", courseId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ));

        mockMvc.perform(put("/system-settings/AI_SIMILARITY_THRESHOLD")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.85\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("0.85"));

        mockMvc.perform(get("/audit-logs")
                        .param("action", "REPORT_DOWNLOADED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
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
