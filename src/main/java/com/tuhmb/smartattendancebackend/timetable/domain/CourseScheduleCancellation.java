package com.tuhmb.smartattendancebackend.timetable.domain;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "course_schedule_cancellations")
public class CourseScheduleCancellation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;
    @Column(name = "to_date")
    private LocalDate toDate;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "cancelled_by", nullable = false)
    private UUID cancelledBy;

    protected CourseScheduleCancellation() {}

    public CourseScheduleCancellation(Course course, LocalDate fromDate, LocalDate toDate,
                                      String reason, UUID cancelledBy) {
        this.course = course;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.cancelledBy = cancelledBy;
    }

    public Course getCourse() { return course; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public String getReason() { return reason; }
    public UUID getCancelledBy() { return cancelledBy; }
}
