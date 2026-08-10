package com.tuhmb.smartattendancebackend.attendance.repository;

import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository
        extends JpaRepository<Attendance, UUID>, JpaSpecificationExecutor<Attendance> {

    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    Optional<Attendance> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    long countByVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(Instant from, Instant to);

    long countByCourseTeacherId(UUID teacherId);

    long countByCourseDepartmentId(UUID departmentId);

    long countByCourseTeacherIdAndVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(
            UUID teacherId,
            Instant from,
            Instant to
    );

    @Query("""
            select attendance.student.id as studentId,
                   attendance.course.id as courseId,
                   count(distinct attendance.session.id) as presentSessions
            from Attendance attendance
            where attendance.session.id in :sessionIds
              and attendance.student.id in :studentIds
            group by attendance.student.id, attendance.course.id
            """)
    List<StudentAttendanceCountProjection> countPresentSessionsByStudentAndCourse(
            @Param("sessionIds") List<UUID> sessionIds,
            @Param("studentIds") List<UUID> studentIds
    );
}
