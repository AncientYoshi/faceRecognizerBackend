package com.tuhmb.smartattendancebackend.timetable.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.timetable.repository.CourseScheduleCancellationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CourseSchedulePolicy {
    private final EntityManager entityManager;
    private final CourseScheduleCancellationRepository cancellations;

    public CourseSchedulePolicy(EntityManager entityManager, CourseScheduleCancellationRepository cancellations) {
        this.entityManager = entityManager;
        this.cancellations = cancellations;
    }

    // All session creation/start paths share this lock with course cancellation.
    public Course lockCourse(UUID id) {
        Course course = entityManager.find(Course.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (course == null) throw new ResourceNotFoundException("Course was not found");
        return course;
    }

    public void refresh(Object entity) { entityManager.refresh(entity); }

    public boolean isCancelled(UUID courseId, LocalDate date) {
        return cancellations.countApplicable(courseId, date) > 0;
    }

    public void requireOpen(UUID courseId, LocalDate date) {
        if (isCancelled(courseId, date)) {
            throw new ConflictException("Attendance is cancelled for this course on the selected date");
        }
    }
}
