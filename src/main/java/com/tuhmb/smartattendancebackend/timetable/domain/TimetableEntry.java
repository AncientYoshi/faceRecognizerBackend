package com.tuhmb.smartattendancebackend.timetable.domain;


import com.tuhmb.smartattendancebackend.academic.domain.Course;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "timetable_entries",
        indexes = {
                @Index(
                        name = "idx_timetable_course",
                        columnList = "course_id"
                ),
                @Index(
                        name = "idx_timetable_day_time",
                        columnList = "day_order,start_time"
                )
        }
)
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "day_order", nullable = false)
    private int dayOrder;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "roll_call_count", nullable = false)
    private int rollCallCount = 1;

    @Column(name = "room", length = 100)
    private String room;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimetableEntry() {
        // Required by JPA
    }

    public TimetableEntry(
            Course course,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            int rollCallCount,
            String room,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {
        update(
                course,
                dayOfWeek,
                startTime,
                endTime,
                rollCallCount,
                room,
                effectiveFrom,
                effectiveTo,
                active
        );
    }

    public void update(
            Course course,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            int rollCallCount,
            String room,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {
        if (rollCallCount < 1 || rollCallCount > 6) {
            throw new IllegalArgumentException("rollCallCount must be between 1 and 6");
        }
        this.course = course;
        this.dayOfWeek = dayOfWeek;
        this.dayOrder = dayOfWeek.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
        this.rollCallCount = rollCallCount;
        this.room = normalizeRoom(room);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (dayOfWeek != null) {
            dayOrder = dayOfWeek.getValue();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();

        if (dayOfWeek != null) {
            dayOrder = dayOfWeek.getValue();
        }
    }

    private String normalizeRoom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getDayOrder() {
        return dayOrder;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getRollCallCount() {
        return rollCallCount;
    }

    public String getRoom() {
        return room;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
