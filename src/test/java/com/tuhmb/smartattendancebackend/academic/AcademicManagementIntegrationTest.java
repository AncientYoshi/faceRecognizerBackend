package com.tuhmb.smartattendancebackend.academic;

import com.jayway.jsonpath.JsonPath;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.timetable.repository.TimetableRepository;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
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

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcademicManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private AttendanceSessionRepository sessionRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private FaceRegistrationRepository faceRegistrationRepository;
    @Autowired
    private TimetableRepository timetableRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
        timetableRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        departmentRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        Role admin = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        userRepository.save(new AppUser(
                "admin@example.com",
                passwordEncoder.encode("password123"),
                "System",
                "Administrator",
                Set.of(admin)
        ));
    }

    @Test
    void completeAcademicAndAttendanceSessionFlowWorks() throws Exception {
        String adminToken = login("admin@example.com", "password123");
        MvcResult teacherUser = createUser(adminToken, """
                {
                  "email": "teacher@example.com",
                  "password": "teacher-password",
                  "firstName": "Grace",
                  "lastName": "Teacher",
                  "roles": ["TEACHER"],
                  "employeeNumber": "TCH-001"
                }
                """);
        String teacherId = JsonPath.read(teacherUser.getResponse().getContentAsString(), "$.teacherId");

        MvcResult studentUser = createUser(adminToken, """
                {
                  "email": "student@example.com",
                  "password": "student-password",
                  "firstName": "Alice",
                  "lastName": "Student",
                  "roles": ["STUDENT"],
                  "studentNumber": "STU-001"
                }
                """);
        String studentId = JsonPath.read(studentUser.getResponse().getContentAsString(), "$.studentId");

        MvcResult department = mockMvc.perform(post("/departments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CSE",
                                  "name": "Computer Science and Engineering",
                                  "description": "Computing department"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CSE"))
                .andReturn();
        String departmentId = JsonPath.read(department.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/departments/{departmentId}/teachers/{teacherId}", departmentId, teacherId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(departmentId));
        mockMvc.perform(put("/departments/{departmentId}/students/{studentId}", departmentId, studentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(departmentId));

        MvcResult course = mockMvc.perform(post("/courses")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CSE-101",
                                  "name": "Introduction to Computing",
                                  "semester": "FIRST",
                                  "academicYear": "2026-2027",
                                  "departmentId": "%s",
                                  "teacherId": "%s"
                                }
                                """.formatted(departmentId, teacherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andReturn();
        String courseId = JsonPath.read(course.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId));

        mockMvc.perform(post("/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("data_conflict"));

        MvcResult timetable = mockMvc.perform(post("/timetables")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "%s",
                                  "dayOfWeek": "MONDAY",
                                  "startTime": "08:30:00",
                                  "endTime": "10:00:00",
                                  "room": "Lab 2",
                                  "effectiveFrom": "2026-08-01",
                                  "effectiveTo": "2026-12-31",
                                  "active": true
                                }
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andReturn();
        String timetableId = JsonPath.read(timetable.getResponse().getContentAsString(), "$.id");

        String studentToken = login("student@example.com", "student-password");
        mockMvc.perform(get("/students/{studentId}/courses", studentId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(courseId));

        mockMvc.perform(get("/students/me/timetable")
                        .queryParam("weekStart", "2026-08-05")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.weekStart").value("2026-08-03"))
                .andExpect(jsonPath("$.weekEnd").value("2026-08-09"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Yangon"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].timetableId").value(timetableId))
                .andExpect(jsonPath("$.entries[0].courseCode").value("CSE-101"))
                .andExpect(jsonPath("$.entries[0].teacherName").value("Grace Teacher"))
                .andExpect(jsonPath("$.entries[0].date").value("2026-08-03"))
                .andExpect(jsonPath("$.entries[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.entries[0].startTime").value("08:30:00"))
                .andExpect(jsonPath("$.entries[0].endTime").value("10:00:00"))
                .andExpect(jsonPath("$.entries[0].room").value("Lab 2"));

        mockMvc.perform(get("/timetables")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        String teacherToken = login("teacher@example.com", "teacher-password");
        mockMvc.perform(get("/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].studentId").value(studentId));

        MvcResult session = mockMvc.perform(post("/attendance-sessions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "%s",
                                  "sessionDate": "2026-08-01",
                                  "startTime": "2026-08-01T01:30:00Z",
                                  "endTime": "2026-08-01T03:00:00Z"
                                }
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        String sessionId = JsonPath.read(session.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/attendance-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/attendance-sessions/{id}/close", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/attendance-sessions/{id}/close", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/attendance-sessions")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "%s",
                                  "sessionDate": "2026-08-02",
                                  "startTime": "2026-08-02T01:30:00Z",
                                  "endTime": "2026-08-02T03:00:00Z"
                                }
                                """.formatted(courseId)))
                .andExpect(status().isForbidden());
    }

    private MvcResult createUser(String adminToken, String body) throws Exception {
        return mockMvc.perform(post("/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
