package com.tuhmb.smartattendancebackend.attendance.domain;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_attendance_session_student",
                columnNames = {"session_id", "student_id"}
        )
)
public class Attendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @Column(name = "attendance_time", nullable = false)
    private Instant attendanceTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "similarity_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected Attendance() {
    }

    public Attendance(
            Student student,
            Course course,
            AttendanceSession session,
            BigDecimal similarityScore,
            Instant verifiedAt
    ) {
        this.student = student;
        this.course = course;
        this.session = session;
        this.attendanceTime = verifiedAt;
        this.status = AttendanceStatus.PRESENT;
        this.similarityScore = similarityScore;
        this.verifiedAt = verifiedAt;
    }

    public Student getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public AttendanceSession getSession() {
        return session;
    }

    public Instant getAttendanceTime() {
        return attendanceTime;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
