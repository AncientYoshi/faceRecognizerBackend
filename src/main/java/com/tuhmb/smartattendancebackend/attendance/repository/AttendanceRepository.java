package com.tuhmb.smartattendancebackend.attendance.repository;

import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;
import java.time.Instant;

public interface AttendanceRepository
        extends JpaRepository<Attendance, UUID>, JpaSpecificationExecutor<Attendance> {

    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    long countByVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(Instant from, Instant to);

    long countByCourseTeacherId(UUID teacherId);

    long countByCourseDepartmentId(UUID departmentId);

    long countByCourseTeacherIdAndVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(
            UUID teacherId,
            Instant from,
            Instant to
    );
}
