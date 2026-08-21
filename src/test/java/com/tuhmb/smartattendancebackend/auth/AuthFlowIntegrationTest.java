package com.tuhmb.smartattendancebackend.auth;

import com.jayway.jsonpath.JsonPath;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private FaceRegistrationRepository faceRegistrationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        teacherRepository.deleteAll();
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
    void loginAccessRefreshLogoutAndReplayProtectionWork() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ADMIN@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String loginBody = login.getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String firstRefreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.firstName").value("System"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefreshToken = JsonPath.read(
                refreshed.getResponse().getContentAsString(),
                "$.refreshToken"
        );

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_failed"));

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginRequestReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void publicDepartmentListAndStudentTeacherRegistrationWork() throws Exception {
        Department department = departmentRepository.save(new Department(
                "REG-CSE",
                "Registration Computer Science",
                "Public registration department"
        ));

        mockMvc.perform(get("/public/departments"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath("$[0].id").value(department.getId().toString()))
                .andExpect(jsonPath("$[0].code").value("REG-CSE"))
                .andExpect(jsonPath("$[0].name").value("Registration Computer Science"))
                .andExpect(jsonPath("$[0].description").doesNotExist());

        MvcResult registeredStudent = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.student@example.com",
                                  "password": "student-password",
                                  "firstName": "New",
                                  "lastName": "Student",
                                  "role": "STUDENT",
                                  "studentNumber": "REG-STU-001",
                                  "studyYear": 5,
                                  "employeeNumber": null,
                                  "departmentId": "%s"
                                }
                                """.formatted(department.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.student@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentNumber").value("REG-STU-001"))
                .andExpect(jsonPath("$.studyYear").value(5))
                .andExpect(jsonPath("$.teacherId").doesNotExist())
                .andExpect(jsonPath("$.departmentId").value(department.getId().toString()))
                .andReturn();
        String studentId = JsonPath.read(registeredStudent.getResponse().getContentAsString(), "$.studentId");
        String studentToken = loginAccessToken("new.student@example.com", "student-password");

        mockMvc.perform(get("/students/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.studyYear").value(5))
                .andExpect(jsonPath("$.departmentId").value(department.getId().toString()))
                .andExpect(jsonPath("$.departmentCode").value("REG-CSE"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.teacher@example.com",
                                  "password": "teacher-password",
                                  "firstName": "New",
                                  "lastName": "Teacher",
                                  "role": "TEACHER",
                                  "studentNumber": null,
                                  "employeeNumber": "REG-TCH-001",
                                  "departmentId": "%s"
                                }
                                """.formatted(department.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.employeeNumber").value("REG-TCH-001"))
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.departmentCode").value("REG-CSE"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin.registration@example.com",
                                  "password": "admin-password",
                                  "firstName": "Invalid",
                                  "lastName": "Admin",
                                  "role": "ADMIN",
                                  "departmentId": "%s"
                                }
                                """.formatted(department.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void adminCannotAssignStudentAndTeacherRolesToOneUser() throws Exception {
        String adminAccessToken = loginAccessToken("admin@example.com", "password123");

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "both.roles@example.com",
                                  "password": "both-role-password",
                                  "firstName": "Both",
                                  "lastName": "Roles",
                                  "roles": ["STUDENT", "TEACHER"],
                                  "studentNumber": "BOTH-STU-001",
                                  "employeeNumber": "BOTH-TCH-001"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("data_conflict"))
                .andExpect(jsonPath("$.message").value("A user cannot have both STUDENT and TEACHER roles"));
    }

    @Test
    void adminCanManageUsersAndNonAdminIsForbidden() throws Exception {
        String adminAccessToken = loginAccessToken("admin@example.com", "password123");

        MvcResult created = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "student-password",
                                  "firstName": "Alice",
                                  "lastName": "Student",
                                  "roles": ["STUDENT"],
                                  "studentNumber": "STU-001",
                                  "studyYear": 5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.studentNumber").value("STU-001"))
                .andExpect(jsonPath("$.studyYear").value(5))
                .andReturn();
        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String studentId = JsonPath.read(created.getResponse().getContentAsString(), "$.studentId");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .queryParam("query", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(userId));

        String studentAccessToken = loginAccessToken("student@example.com", "student-password");

        mockMvc.perform(get("/students/me")
                        .header("Authorization", "Bearer " + studentAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.studentNumber").value("STU-001"))
                .andExpect(jsonPath("$.studyYear").value(5))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Student"))
                .andExpect(jsonPath("$.departmentId").doesNotExist());

        mockMvc.perform(get("/students/me")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + studentAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "teacher@example.com",
                                  "firstName": "Alice",
                                  "lastName": "Teacher",
                                  "enabled": true,
                                  "roles": ["TEACHER"],
                                  "employeeNumber": "TCH-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNumber").doesNotExist())
                .andExpect(jsonPath("$.employeeNumber").value("TCH-001"))
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());
    }

    private String loginAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }
}
