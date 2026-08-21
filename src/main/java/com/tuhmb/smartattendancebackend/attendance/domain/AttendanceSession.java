package com.tuhmb.smartattendancebackend.attendance.domain;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "roll_call_count", nullable = false)
    private int rollCallCount = 1;

    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceSessionStatus status = AttendanceSessionStatus.SCHEDULED;

    protected AttendanceSession() {
    }

    public AttendanceSession(Course course, Teacher teacher, LocalDate sessionDate, Instant startTime, Instant endTime) {
        this(course, teacher, sessionDate, startTime, endTime, 1);
    }

    public AttendanceSession(
            Course course,
            Teacher teacher,
            LocalDate sessionDate,
            Instant startTime,
            Instant endTime,
            int rollCallCount
    ) {
        this.course = course;
        this.teacher = teacher;
        updateSchedule(sessionDate, startTime, endTime, rollCallCount);
    }

    public void assignIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void updateSchedule(LocalDate sessionDate, Instant startTime, Instant endTime) {
        updateSchedule(sessionDate, startTime, endTime, rollCallCount);
    }

    public void updateSchedule(LocalDate sessionDate, Instant startTime, Instant endTime, int rollCallCount) {
        if (status != null && status != AttendanceSessionStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled attendance sessions can be updated");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ConflictException("Session end time must be after its start time");
        }
        if (rollCallCount < 1 || rollCallCount > 6) {
            throw new ConflictException("rollCallCount must be between 1 and 6");
        }
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.rollCallCount = rollCallCount;
    }

    public void start() {
        requireStatus(AttendanceSessionStatus.SCHEDULED, "Only a scheduled session can be started");
        status = AttendanceSessionStatus.ACTIVE;
    }

    public void close() {
        requireStatus(AttendanceSessionStatus.ACTIVE, "Only an active session can be closed");
        status = AttendanceSessionStatus.CLOSED;
    }

    public void cancel() {
        requireStatus(AttendanceSessionStatus.SCHEDULED, "Only a scheduled session can be cancelled");
        status = AttendanceSessionStatus.CANCELLED;
    }

    private void requireStatus(AttendanceSessionStatus expected, String message) {
        if (status != expected) {
            throw new ConflictException(message);
        }
    }

    public Course getCourse() {
        return course;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getRollCallCount() {
        return rollCallCount;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public AttendanceSessionStatus getStatus() {
        return status;
    }
}
