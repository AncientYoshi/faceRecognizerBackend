package com.tuhmb.smartattendancebackend.timetable;

import com.tuhmb.smartattendancebackend.academic.domain.*;
import com.tuhmb.smartattendancebackend.academic.repository.*;
import com.tuhmb.smartattendancebackend.attendance.domain.*;
import com.tuhmb.smartattendancebackend.attendance.repository.*;
import com.tuhmb.smartattendancebackend.attendance.service.*;
import com.tuhmb.smartattendancebackend.timetable.api.CourseScheduleCancellationController.Request;
import com.tuhmb.smartattendancebackend.timetable.api.CourseScheduleCancellationController.Scope;
import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import com.tuhmb.smartattendancebackend.timetable.repository.*;
import com.tuhmb.smartattendancebackend.timetable.service.*;
import com.tuhmb.smartattendancebackend.user.domain.*;
import com.tuhmb.smartattendancebackend.user.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseScheduleCancellationIntegrationTest {
    @Autowired CourseScheduleCancellationService service;
    @Autowired CourseScheduleCancellationRepository cancellations;
    @Autowired CourseSchedulePolicy policy;
    @Autowired AttendanceSessionAutomationService automation;
    @Autowired AttendanceSessionService sessionService;
    @Autowired AttendanceSessionRepository sessions;
    @Autowired AttendanceRepository attendance;
    @Autowired CourseRepository courses;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TeacherRepository teachers;
    @Autowired StudentRepository students;
    @Autowired EnrollmentRepository enrollments;
    @Autowired TimetableRepository timetables;
    @Autowired StudentTimetableService studentTimetable;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    private Course course;
    private Teacher teacher;
    private Student student;
    private Jwt teacherJwt;
    private LocalDate today;
    private final ZoneId zone = ZoneId.of("Asia/Yangon");

    @BeforeEach
    void setup() {
        today = LocalDate.now(zone);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var teacherUser = users.save(new AppUser(suffix + "@example.com", "unused", "Test", "Teacher",
                Set.of(roles.findByName(RoleName.TEACHER).orElseThrow())));
        var department = departments.save(new Department(suffix, "Cancellation tests", null));
        teacher = new Teacher(teacherUser, "T-" + suffix);
        teacher.assignDepartment(department);
        teachers.save(teacher);
        course = courses.save(new Course("C-" + suffix, "Cancellation course", "1", "2026-2027", 5, department, teacher));
        var studentUser = users.save(new AppUser("s-" + suffix + "@example.com", "unused", "Test", "Student",
                Set.of(roles.findByName(RoleName.STUDENT).orElseThrow())));
        student = new Student(studentUser, "S-" + suffix, 5);
        student.assignDepartment(department);
        students.save(student);
        enrollments.save(new Enrollment(student, course));
        teacherJwt = token(teacherUser.getId(), "TEACHER");
        em.flush();
    }

    @Test
    void todayCancellationBeforeGenerationSurvivesSchedulerAndLeavesNextWeekAvailable() {
        var timetable = timetables.save(new TimetableEntry(course, today.getDayOfWeek(),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 1, null, today, null, true));
        em.flush();
        service.cancel(course.getId(), new Request(Scope.TODAY, null, "Sick leave"), teacherJwt);
        automation.synchronizeAt(at(today, 9));
        automation.synchronizeAt(at(today, 9));
        assertThat(sessions.findByTimetableEntryIdAndSessionDate(timetable.getId(), today)).isEmpty();
        assertThat(studentTimetable.getWeek(token(student.getUser().getId(), "STUDENT"), today).entries()).isEmpty();
        automation.synchronizeAt(at(today.plusWeeks(1), 9));
        assertThat(sessions.findByTimetableEntryIdAndSessionDate(timetable.getId(), today.plusWeeks(1)))
                .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(AttendanceSessionStatus.ACTIVE));
        assertThat(studentTimetable.getWeek(token(student.getUser().getId(), "STUDENT"), today.plusWeeks(1)).entries()).hasSize(1);
    }

    @Test
    void futureCancellationCancelsExistingSessionsAndBlocksGenerationFromInclusiveDate() {
        var tomorrow = session(today.plusDays(1));
        var future = session(today.plusWeeks(1));
        var past = session(today.minusDays(1));
        past.start(); past.close();
        var todaySession = session(today);
        em.flush();
        var response = service.cancel(course.getId(), new Request(Scope.FUTURE, today.plusDays(1), "Course finished"), teacherJwt);
        assertThat(response.cancelledSessions()).isEqualTo(2);
        assertThat(tomorrow.getStatus()).isEqualTo(AttendanceSessionStatus.CANCELLED);
        assertThat(future.getStatus()).isEqualTo(AttendanceSessionStatus.CANCELLED);
        assertThat(past.getStatus()).isEqualTo(AttendanceSessionStatus.CLOSED);
        assertThat(todaySession.getStatus()).isEqualTo(AttendanceSessionStatus.SCHEDULED);
        assertThat(policy.isCancelled(course.getId(), today.plusYears(1))).isTrue();
        assertThat(policy.isCancelled(course.getId(), today)).isFalse();
        assertThatThrownBy(() -> sessionService.start(future.getId(), teacherJwt))
                .hasMessageContaining("cancelled");
    }

    @Test
    void activeCancellationPreservesRecordedEvidenceButRemovesEligibleRollCallsAndIsIdempotent() {
        var current = session(today);
        current.start();
        attendance.save(new Attendance(student, course, current, new BigDecimal("0.95"), at(today, 9)));
        em.flush();
        var request = new Request(Scope.TODAY, null, "Teacher unavailable");
        var first = service.cancel(course.getId(), request, teacherJwt);
        var second = service.cancel(course.getId(), request, teacherJwt);
        assertThat(first.cancellation().id()).isEqualTo(second.cancellation().id());
        assertThat(second.cancelledSessions()).isZero();
        assertThat(current.getStatus()).isEqualTo(AttendanceSessionStatus.CANCELLED);
        assertThat(attendance.existsBySessionIdAndStudentId(current.getId(), student.getId())).isTrue();
        assertThat(sessions.sumCohortRollCallsForDate(course.getDepartment().getId(), 5, today,
                AttendanceSessionStatus.CANCELLED, null)).isZero();
        automation.synchronizeAt(at(today, 11));
        assertThat(current.getStatus()).isEqualTo(AttendanceSessionStatus.CANCELLED);
    }

    @Test
    void rejectsAnotherTeacherAndStudentAndInvalidRequests() throws Exception {
        var other = token(UUID.randomUUID(), "TEACHER");
        mvc.perform(post(endpoint()).with(jwt().jwt(other).authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"TODAY\",\"reason\":\"No class\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint()).with(jwt().jwt(token(student.getUser().getId(), "STUDENT"))
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"TODAY\",\"reason\":\"No class\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint()).with(jwt().jwt(teacherJwt).authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"TODAY\",\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(endpoint()).with(jwt().jwt(teacherJwt).authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"FUTURE\",\"fromDate\":\"2000-01-01\",\"reason\":\"Finished\"}"))
                .andExpect(status().isBadRequest());
        assertThat(cancellations.findByCourseIdOrderByCreatedAtDesc(course.getId())).isEmpty();
    }

    @Test
    void teacherCanReadContextAndCancelViaApi() throws Exception {
        mvc.perform(get(endpoint()).with(jwt().jwt(teacherJwt).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.timeZone").value("Asia/Yangon"));
        mvc.perform(post(endpoint()).with(jwt().jwt(teacherJwt).authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"TODAY\",\"reason\":\"Sick leave\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellation.toDate").value(today.toString()));
    }

    private String endpoint() { return "/courses/" + course.getId() + "/schedule-cancellations"; }
    private Instant at(LocalDate date, int hour) { return date.atTime(hour, 0).atZone(zone).toInstant(); }
    private AttendanceSession session(LocalDate date) {
        return sessions.save(new AttendanceSession(course, teacher, date, at(date, 9), at(date, 10), 1));
    }
    private Jwt token(UUID user, String role) {
        return Jwt.withTokenValue("test").header("alg", "none").subject(user.toString()).claim("roles", List.of(role)).build();
    }
}
