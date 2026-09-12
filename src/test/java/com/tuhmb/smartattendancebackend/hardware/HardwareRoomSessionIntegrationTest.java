package com.tuhmb.smartattendancebackend.hardware;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.hardware.domain.HardwareDevice;
import com.tuhmb.smartattendancebackend.hardware.repository.HardwareDeviceRepository;
import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import com.tuhmb.smartattendancebackend.timetable.repository.TimetableRepository;
import com.tuhmb.smartattendancebackend.user.domain.*;
import com.tuhmb.smartattendancebackend.user.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HardwareRoomSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired CourseRepository courses;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TeacherRepository teachers;
    @Autowired AttendanceSessionRepository sessions;
    @Autowired TimetableRepository timetables;
    @Autowired HardwareDeviceRepository devices;
    @Autowired PasswordEncoder encoder;

    private Course course;
    private HardwareDevice device;
    private String room;
    private final String key = "test-hardware-room-device-key";
    private Instant now;
    private LocalDate today;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        room = "Lab-" + suffix;
        now = Instant.now();
        today = LocalDate.now(ZoneId.of("Asia/Yangon"));
        var department = departments.save(new Department(suffix, "Hardware rooms", null));
        var user = users.save(new AppUser(suffix + "@example.com", "unused", "Room", "Teacher",
                Set.of(roles.findByName(RoleName.TEACHER).orElseThrow())));
        var teacher = new Teacher(user, "T-" + suffix);
        teacher.assignDepartment(department);
        teachers.save(teacher);
        course = courses.save(new Course("C-" + suffix, "Room course", "1", "2026-2027", 5, department, teacher));
        device = devices.save(new HardwareDevice("ROOM-" + suffix, "Test camera", encoder.encode(key),
                "  " + room.toUpperCase(Locale.ROOT) + "  ", null, true));
        em.flush();
    }

    @Test
    void manualApiSessionPersistsRoomAndIsDiscoveredWithoutCourseRestriction() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        mvc.perform(asTeacher(post("/attendance-sessions"))
                        .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("  " + room + "  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.room").value(room));
        em.flush();
        em.clear();
        var created = sessions.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(created.getTimetableEntry()).isNull();
        mvc.perform(asTeacher(post("/attendance-sessions/{id}/start", created.getId())))
                .andExpect(status().isOk());
        mvc.perform(discover()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(created.getId().toString()))
                .andExpect(jsonPath("$.room").value(room));
    }

    @Test
    void roomOnlyDeviceCanDiscoverAnotherCourseInTheSameRoom() throws Exception {
        var other = courses.save(new Course("O-" + UUID.randomUUID(), "Other course", "1", "2026-2027",
                5, course.getDepartment(), course.getTeacher()));
        var session = active(other, room);
        active(course, "Different room");
        mvc.perform(discover()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().toString()));
    }

    @Test
    void roomAndCourseBindingAlsoFindsManualSessions() throws Exception {
        device.update(device.getName(), room, course, true);
        var session = active(course, room);
        mvc.perform(discover()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().toString()));
    }

    @Test
    void wrongCourseBindingDoesNotMatchSameRoom() throws Exception {
        var other = courses.save(new Course("O-" + UUID.randomUUID(), "Other course", "1", "2026-2027",
                5, course.getDepartment(), course.getTeacher()));
        device.update(device.getName(), room, other, true);
        active(course, room);
        mvc.perform(discover()).andExpect(status().isConflict());
    }

    @Test
    void roomlessWrongRoomFutureAndClosedSessionsAreExcluded() throws Exception {
        active(course, null);
        active(course, "Somewhere else");
        active(course, room).close();
        var future = new AttendanceSession(course, course.getTeacher(), today, now.plusSeconds(3600), now.plusSeconds(7200));
        future.assignRoom(room);
        future.start();
        sessions.save(future);
        mvc.perform(discover()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No active attendance session matches this hardware device"));
    }

    @Test
    void multipleActiveSessionsInSameRoomRemainAmbiguous() throws Exception {
        active(course, room);
        active(course, room);
        mvc.perform(discover()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Multiple active attendance sessions match this hardware device; narrow its room/course binding"));
    }

    @Test
    void timetableRoomStillWorksAndExplicitSessionRoomTakesPrecedence() throws Exception {
        var entry = timetables.save(new TimetableEntry(course, today.getDayOfWeek(), LocalTime.of(9, 0),
                LocalTime.of(10, 0), 1, room, today, null, true));
        var automatic = active(course, null);
        automatic.linkTimetableEntry(entry);
        var overridden = new AttendanceSession(course, course.getTeacher(), today, now.minusSeconds(60), now.plusSeconds(3600));
        overridden.assignRoom("Other lab");
        overridden.linkTimetableEntry(entry);
        // Use a different date to respect the timetable/date unique constraint.
        overridden.updateSchedule(today.minusDays(1), now.minusSeconds(60), now.plusSeconds(3600));
        overridden.start();
        sessions.save(overridden);
        mvc.perform(discover()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(automatic.getId().toString()))
                .andExpect(jsonPath("$.room").value(room));
    }

    @Test
    void identifyUsesManualRoomForBothDiscoveryAndExplicitSessionId() throws Exception {
        var session = active(course, room);
        var image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        for (boolean explicit : List.of(false, true)) {
            var request = multipart("/hardware/v1/attendance/identify").file(image);
            if (explicit) request.param("sessionId", session.getId().toString());
            mvc.perform(request.header("X-Device-Id", device.getDeviceId()).header("X-Device-Key", key))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(session.getId().toString()))
                    .andExpect(jsonPath("$.reason").value("NO_REGISTERED_STUDENTS"));
        }
        device.update(device.getName(), "Wrong lab", null, true);
        mvc.perform(multipart("/hardware/v1/attendance/identify").file(image)
                        .param("sessionId", session.getId().toString())
                        .header("X-Device-Id", device.getDeviceId()).header("X-Device-Key", key))
                .andExpect(status().isConflict());
    }

    @Test
    void updatePersistsRoomAndIdempotencyDetectsRoomChanges() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        mvc.perform(asTeacher(post("/attendance-sessions"))
                        .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(payload(room)))
                .andExpect(status().isCreated());
        mvc.perform(asTeacher(post("/attendance-sessions"))
                        .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(payload("Different")))
                .andExpect(status().isConflict());
        var session = sessions.findByIdempotencyKey(idempotencyKey).orElseThrow();
        mvc.perform(asTeacher(put("/attendance-sessions/{id}", session.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(payload("Updated lab")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.room").value("Updated lab"));
        em.flush();
        em.clear();
        assertThat(sessions.findById(session.getId()).orElseThrow().getRoom()).isEqualTo("Updated lab");
    }

    @Test
    void roomLengthIsValidated() throws Exception {
        mvc.perform(asTeacher(post("/attendance-sessions"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload("a".repeat(101))))
                .andExpect(status().isBadRequest());
    }

    private AttendanceSession active(Course target, String sessionRoom) {
        var session = new AttendanceSession(target, target.getTeacher(), today, now.minusSeconds(60), now.plusSeconds(3600));
        session.assignRoom(sessionRoom);
        session.start();
        return sessions.save(session);
    }

    private MockHttpServletRequestBuilder discover() {
        em.flush();
        return get("/hardware/v1/attendance/active-session")
                .header("X-Device-Id", device.getDeviceId()).header("X-Device-Key", key);
    }

    private MockHttpServletRequestBuilder asTeacher(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(builder -> builder.subject(course.getTeacher().getUser().getId().toString())
                        .claim("roles", List.of("TEACHER")))
                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private String payload(String sessionRoom) {
        return """
                {"courseId":"%s","sessionDate":"%s","startTime":"%s","endTime":"%s","rollCallCount":1,"room":"%s"}
                """.formatted(course.getId(), today, now.minusSeconds(60), now.plusSeconds(3600), sessionRoom);
    }
}
