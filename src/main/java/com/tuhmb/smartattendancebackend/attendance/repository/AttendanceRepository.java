package com.tuhmb.smartattendancebackend.attendance.repository;

import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceStatus;
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

    @Query("""
            select count(distinct attendance.session.id)
            from Attendance attendance
            where attendance.student.id = :studentId
              and attendance.status = :presentStatus
              and attendance.session.course.id in (
                  select enrollment.course.id
                  from Enrollment enrollment
                  where enrollment.student.id = :studentId
              )
              and attendance.session.status <> :cancelledStatus
              and attendance.session.startTime <= :asOf
              and (attendance.session.status = :closedStatus or attendance.session.endTime <= :asOf)
            """)
    long countStudentPresentEligibleSessions(
            @Param("studentId") UUID studentId,
            @Param("presentStatus") AttendanceStatus presentStatus,
            @Param("cancelledStatus") AttendanceSessionStatus cancelledStatus,
            @Param("closedStatus") AttendanceSessionStatus closedStatus,
            @Param("asOf") Instant asOf
    );

    @Query("""
            select count(distinct attendance.session.id)
            from Attendance attendance
            where attendance.student.id = :studentId
              and attendance.status = :presentStatus
              and attendance.session.course.id in (
                  select enrollment.course.id
                  from Enrollment enrollment
                  where enrollment.student.id = :studentId
              )
              and attendance.session.sessionDate between :from and :to
              and attendance.session.status <> :cancelledStatus
              and attendance.session.startTime <= :asOf
              and (attendance.session.status = :closedStatus or attendance.session.endTime <= :asOf)
            """)
    long countStudentPresentEligibleSessionsBetween(
            @Param("studentId") UUID studentId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to,
            @Param("presentStatus") AttendanceStatus presentStatus,
            @Param("cancelledStatus") AttendanceSessionStatus cancelledStatus,
            @Param("closedStatus") AttendanceSessionStatus closedStatus,
            @Param("asOf") Instant asOf
    );

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
