package com.tuhmb.smartattendancebackend.auth;

import com.jayway.jsonpath.JsonPath;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
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
                                  "studentNumber": "STU-001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.studentNumber").value("STU-001"))
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
