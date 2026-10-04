package com.tuhmb.smartattendancebackend.timetable;

import com.tuhmb.smartattendancebackend.academic.domain.*;
import com.tuhmb.smartattendancebackend.academic.repository.*;
import com.tuhmb.smartattendancebackend.timetable.api.TimetableRequest;
import com.tuhmb.smartattendancebackend.timetable.service.TimetableService;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.user.domain.*;
import com.tuhmb.smartattendancebackend.user.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

/** Run with PostgreSQL datasource overrides as well as the default H2 test profile. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimetableParameterIntegrationTest {
    @Autowired TimetableService service;
    @Autowired CourseRepository courses;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TeacherRepository teachers;
    private Course course;

    @BeforeEach
    void setup() {
        String code = UUID.randomUUID().toString().substring(0, 8);
        var department = departments.save(new Department(code, "Parameter tests", null));
        var user = users.save(new AppUser(code + "@example.com", "unused", "Test", "Teacher",
                Set.of(roles.findByName(RoleName.TEACHER).orElseThrow())));
        var teacher = new Teacher(user, "T-" + code);
        teacher.assignDepartment(department);
        teachers.save(teacher);
        course = courses.save(new Course(code, "Test course", "1", "2026-2027", 5, department, teacher));
    }

    @Test
    void createsReportedDateRangeAndUpdatesWithoutCountingItself() {
        var request = request(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 30));
        var created = service.create(request);
        assertThat(created.rollCallCount()).isEqualTo(6);
        assertThat(service.update(created.id(), request).id()).isEqualTo(created.id());
    }

    @Test
    void acceptsOpenEndedDateRanges() {
        var created = service.create(request(null, null));
        assertThat(service.update(created.id(), request(LocalDate.of(2026, 10, 4), null)).effectiveTo()).isNull();
        assertThat(service.update(created.id(), request(null, LocalDate.of(2026, 10, 30))).effectiveFrom()).isNull();
    }

    @Test
    void retainsCohortLimitAndAllowsNonOverlappingPeriods() {
        service.create(request(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 30)));
        assertThatThrownBy(() -> service.create(request(LocalDate.of(2026, 10, 30), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("6 timetable roll calls");
        assertThat(service.create(request(LocalDate.of(2026, 10, 31), null)).id()).isNotNull();
    }

    private TimetableRequest request(LocalDate from, LocalDate to) {
        return new TimetableRequest(course.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(8, 30), LocalTime.of(15, 0), null, "Online", from, to, true);
    }
}
