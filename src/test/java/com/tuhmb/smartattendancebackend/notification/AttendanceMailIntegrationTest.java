package com.tuhmb.smartattendancebackend.notification;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.notifications.mail.enabled=true",
        "app.notifications.mail.from=no-reply@example.com",
        "app.notifications.mail.frontend-base-url=https://frontend.example.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AttendanceMailIntegrationTest.MailTestConfiguration.class)
class AttendanceMailIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired RecordingMailSender mailSender;
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

    private UUID sessionId;

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
        mailSender.reset(1);
        transactionTemplate.executeWithoutResult(status -> createSession());
    }

    @Test
    void startingSessionEmailsEveryEnabledEnrolledStudentAfterCommit() throws Exception {
        String teacherToken = login("mail.teacher@example.com", "teacher-password");

        mockMvc.perform(post("/attendance-sessions/{id}/start", sessionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        assertThat(mailSender.await()).isTrue();
        assertThat(mailSender.messages()).hasSize(1);
        SimpleMailMessage message = mailSender.messages().getFirst();
        assertThat(message.getTo()).containsExactly("mail.student@example.com");
        assertThat(message.getText())
                .contains("Mail Course")
                .contains("https://frontend.example.com/student/scan/" + sessionId);
    }

    private void createSession() {
        Role teacherRole = roleRepository.findByName(RoleName.TEACHER).orElseThrow();
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
        AppUser teacherUser = userRepository.save(new AppUser(
                "mail.teacher@example.com",
                passwordEncoder.encode("teacher-password"),
                "Mail",
                "Teacher",
                Set.of(teacherRole)
        ));
        AppUser studentUser = userRepository.save(new AppUser(
                "mail.student@example.com",
                passwordEncoder.encode("student-password"),
                "Mail",
                "Student",
                Set.of(studentRole)
        ));
        Department department = departmentRepository.save(
                new Department("MAIL", "Mail Department", null)
        );
        Teacher teacher = new Teacher(teacherUser, "MAIL-T-001");
        teacher.assignDepartment(department);
        teacherRepository.save(teacher);
        Student student = new Student(studentUser, "MAIL-S-001");
        student.assignDepartment(department);
        studentRepository.save(student);
        Course course = courseRepository.save(new Course(
                "MAIL-101",
                "Mail Course",
                "FIRST",
                "2026-2027",
                department,
                teacher
        ));
        enrollmentRepository.save(new Enrollment(student, course));
        Instant now = Instant.now();
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
                course,
                teacher,
                LocalDate.now(ZoneId.of("Asia/Yangon")),
                now.minusSeconds(60),
                now.plusSeconds(3_600)
        ));
        sessionId = session.getId();
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

    @TestConfiguration
    static class MailTestConfiguration {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    static final class RecordingMailSender extends JavaMailSenderImpl implements JavaMailSender {

        private final List<SimpleMailMessage> messages = new ArrayList<>();
        private CountDownLatch latch = new CountDownLatch(0);

        synchronized void reset(int expectedMessages) {
            messages.clear();
            latch = new CountDownLatch(expectedMessages);
        }

        @Override
        public synchronized void send(SimpleMailMessage simpleMessage) {
            messages.add(new SimpleMailMessage(simpleMessage));
            latch.countDown();
        }

        synchronized List<SimpleMailMessage> messages() {
            return List.copyOf(messages);
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }
}
