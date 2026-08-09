package com.tuhmb.smartattendancebackend.face;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.face.client.FaceAiClient;
import com.tuhmb.smartattendancebackend.face.client.IdentifyFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.client.RegisterFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.client.VerifyFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.face.domain.FaceRegistration;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiIntegrationFlowTest.FakeAiConfiguration.class)
class AiIntegrationFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeacherRepository teacherRepository;
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
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestFaceAiClient faceAiClient;

    private UUID studentId;
    private UUID firstSessionId;
    private UUID secondSessionId;

    @BeforeEach
    void setUp() {
        faceAiClient.reset();
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        departmentRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        transactionTemplate.executeWithoutResult(status -> {
            Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
            Role teacherRole = roleRepository.findByName(RoleName.TEACHER).orElseThrow();
            AppUser studentUser = userRepository.save(new AppUser(
                    "student@example.com",
                    passwordEncoder.encode("student-password"),
                    "Alice",
                    "Student",
                    Set.of(studentRole)
            ));
            AppUser teacherUser = userRepository.save(new AppUser(
                    "teacher@example.com",
                    passwordEncoder.encode("teacher-password"),
                    "Grace",
                    "Teacher",
                    Set.of(teacherRole)
            ));
            Department department = departmentRepository.save(
                    new Department("CSE", "Computer Science", null)
            );
            Student student = new Student(studentUser, "STU-AI-001");
            student.assignDepartment(department);
            studentRepository.save(student);
            Teacher teacher = new Teacher(teacherUser, "TCH-AI-001");
            teacher.assignDepartment(department);
            teacherRepository.save(teacher);
            Course course = courseRepository.save(new Course(
                    "CSE-AI-101",
                    "AI Attendance",
                    "FIRST",
                    "2026-2027",
                    department,
                    teacher
            ));
            enrollmentRepository.save(new Enrollment(student, course));

            Instant now = Instant.now();
            AttendanceSession first = new AttendanceSession(
                    course,
                    teacher,
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    now.minusSeconds(60),
                    now.plusSeconds(600)
            );
            first.start();
            sessionRepository.save(first);
            AttendanceSession second = new AttendanceSession(
                    course,
                    teacher,
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    now.minusSeconds(60),
                    now.plusSeconds(600)
            );
            second.start();
            sessionRepository.save(second);

            studentId = student.getId();
            firstSessionId = first.getId();
            secondSessionId = second.getId();
        });
    }

    @Test
    void registerVerifyPersistRejectDuplicateAndHandleNoMatch() throws Exception {
        String studentToken = login("student@example.com", "student-password");
        MockMultipartFile image = faceImage();

        faceAiClient.registrationResponse = new RegisterFaceAiResponse(
                true,
                "embedding-" + studentId
        );

        mockMvc.perform(multipart("/students/{studentId}/face/register", studentId)
                        .file(image)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.embeddingId").value("embedding-" + studentId));

        mockMvc.perform(get("/students/{studentId}/face", studentId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingId").value("embedding-" + studentId));

        faceAiClient.verificationResponse = new VerifyFaceAiResponse(true, 0.93456);

        MvcResult verified = mockMvc.perform(multipart("/attendance/verify")
                        .file(faceImage())
                        .param("sessionId", firstSessionId.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.similarity").value(0.93456))
                .andReturn();
        String attendanceId = com.jayway.jsonpath.JsonPath.read(
                verified.getResponse().getContentAsString(),
                "$.attendanceId"
        );

        mockMvc.perform(get("/attendance/{id}", attendanceId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(firstSessionId.toString()))
                .andExpect(jsonPath("$.studentId").value(studentId.toString()));

        mockMvc.perform(multipart("/attendance/verify")
                        .file(faceImage())
                        .param("sessionId", firstSessionId.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("data_conflict"));
        assertEquals(1, faceAiClient.verifyCalls.get());

        faceAiClient.verificationResponse = new VerifyFaceAiResponse(false, 0.41234);
        mockMvc.perform(multipart("/attendance/verify")
                        .file(faceImage())
                        .param("sessionId", secondSessionId.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.attendanceId").doesNotExist())
                .andExpect(jsonPath("$.similarity").value(0.41234));

        mockMvc.perform(get("/attendance")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        String teacherToken = login("teacher@example.com", "teacher-password");
        mockMvc.perform(get("/attendance")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].attendanceId").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(attendanceId));
    }

    @Test
    void esp32IdentifiesStudentRecordsAttendanceAndHandlesDuplicate() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            Student student = studentRepository.findById(studentId).orElseThrow();
            faceRegistrationRepository.save(new FaceRegistration(student, "embedding-" + studentId));
        });

        faceAiClient.identificationResponse = new IdentifyFaceAiResponse(
                true,
                studentId,
                0.95678,
                true,
                "MATCHED"
        );

        mockMvc.perform(multipart("/hardware/v1/attendance/identify")
                        .file(faceImage())
                        .param("sessionId", firstSessionId.toString())
                        .header("X-Device-Id", "CLASSROOM-01")
                        .header("X-Device-Key", "wrong-device-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_device_credentials"));
        assertEquals(0, faceAiClient.identifyCalls.get());

        faceAiClient.identificationResponse = new IdentifyFaceAiResponse(
                true,
                studentId,
                0.95678,
                false,
                "MATCHED"
        );
        mockMvc.perform(multipart("/hardware/v1/attendance/identify")
                        .file(faceImage())
                        .param("sessionId", secondSessionId.toString())
                        .header("X-Device-Id", "CLASSROOM-01")
                        .header("X-Device-Key", "change-this-device-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.reason").value("LIVENESS_FAILED"))
                .andExpect(jsonPath("$.attendanceRecorded").value(false));
        assertEquals(0, attendanceRepository.count());

        faceAiClient.identificationResponse = new IdentifyFaceAiResponse(
                true,
                studentId,
                0.95678,
                true,
                "MATCHED"
        );

        MvcResult recorded = mockMvc.perform(multipart("/hardware/v1/attendance/identify")
                        .file(faceImage())
                        .param("sessionId", firstSessionId.toString())
                        .header("X-Device-Id", "CLASSROOM-01")
                        .header("X-Device-Key", "change-this-device-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.reason").value("ATTENDANCE_VERIFIED"))
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.rollNumber").value("STU-AI-001"))
                .andExpect(jsonPath("$.attendanceRecorded").value(true))
                .andExpect(jsonPath("$.similarity").value(0.95678))
                .andReturn();
        String attendanceId = com.jayway.jsonpath.JsonPath.read(
                recorded.getResponse().getContentAsString(),
                "$.attendanceId"
        );
        assertEquals(List.of(studentId), faceAiClient.lastCandidateStudentIds);

        mockMvc.perform(multipart("/hardware/v1/attendance/identify")
                        .file(faceImage())
                        .param("sessionId", firstSessionId.toString())
                        .header("X-Device-Id", "CLASSROOM-01")
                        .header("X-Device-Key", "change-this-device-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.reason").value("ALREADY_VERIFIED"))
                .andExpect(jsonPath("$.attendanceRecorded").value(false))
                .andExpect(jsonPath("$.attendanceId").value(attendanceId));

        assertEquals(1, attendanceRepository.count());
        assertEquals(3, faceAiClient.identifyCalls.get());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private MockMultipartFile faceImage() {
        return new MockMultipartFile(
                "image",
                "face.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3, 4}
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfiguration {

        @Bean
        @Primary
        TestFaceAiClient testFaceAiClient() {
            return new TestFaceAiClient();
        }
    }

    static class TestFaceAiClient implements FaceAiClient {

        private RegisterFaceAiResponse registrationResponse;
        private VerifyFaceAiResponse verificationResponse;
        private IdentifyFaceAiResponse identificationResponse;
        private List<UUID> lastCandidateStudentIds = List.of();
        private final AtomicInteger registerCalls = new AtomicInteger();
        private final AtomicInteger verifyCalls = new AtomicInteger();
        private final AtomicInteger identifyCalls = new AtomicInteger();

        @Override
        public RegisterFaceAiResponse register(String studentId, MultipartFile image) {
            registerCalls.incrementAndGet();
            return registrationResponse;
        }

        @Override
        public VerifyFaceAiResponse verify(String studentId, MultipartFile image) {
            verifyCalls.incrementAndGet();
            return verificationResponse;
        }

        @Override
        public IdentifyFaceAiResponse identify(List<UUID> candidateStudentIds, MultipartFile image) {
            identifyCalls.incrementAndGet();
            lastCandidateStudentIds = List.copyOf(candidateStudentIds);
            return identificationResponse;
        }

        private void reset() {
            registrationResponse = null;
            verificationResponse = null;
            identificationResponse = null;
            lastCandidateStudentIds = List.of();
            registerCalls.set(0);
            verifyCalls.set(0);
            identifyCalls.set(0);
        }
    }
}
