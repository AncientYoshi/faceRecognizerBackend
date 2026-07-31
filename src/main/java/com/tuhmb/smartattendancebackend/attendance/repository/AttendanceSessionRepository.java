package com.tuhmb.smartattendancebackend.attendance.repository;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;

public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, UUID>, JpaSpecificationExecutor<AttendanceSession> {

    long countByCourseId(UUID courseId);

    List<AttendanceSession> findTop5ByOrderByStartTimeDesc();

    List<AttendanceSession> findByTeacherIdAndSessionDateOrderByStartTimeAsc(UUID teacherId, LocalDate date);

    List<AttendanceSession> findByStatusNotAndStartTimeLessThanEqual(
            AttendanceSessionStatus status,
            Instant now
    );

    List<AttendanceSession> findByTeacherIdAndStatusNotAndStartTimeLessThanEqual(
            UUID teacherId,
            AttendanceSessionStatus status,
            Instant now
    );
}
