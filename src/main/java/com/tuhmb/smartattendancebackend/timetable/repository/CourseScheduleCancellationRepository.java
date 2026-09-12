package com.tuhmb.smartattendancebackend.timetable.repository;

import com.tuhmb.smartattendancebackend.timetable.domain.CourseScheduleCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CourseScheduleCancellationRepository extends JpaRepository<CourseScheduleCancellation, UUID> {
    List<CourseScheduleCancellation> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    @Query("""
            select count(c) from CourseScheduleCancellation c
            where c.course.id = :courseId and c.fromDate <= :date
              and (c.toDate is null or c.toDate >= :date)
            """)
    long countApplicable(@Param("courseId") UUID courseId, @Param("date") LocalDate date);
}
