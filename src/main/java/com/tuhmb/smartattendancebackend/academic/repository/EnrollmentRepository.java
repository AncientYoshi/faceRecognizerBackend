package com.tuhmb.smartattendancebackend.academic.repository;

import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Page<Enrollment> findByCourseId(UUID courseId, Pageable pageable);

    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

    long countByCourseId(UUID courseId);

    long countByStudentId(UUID studentId);

    @Query("""
            select enrollment.student.id
            from Enrollment enrollment
            where enrollment.course.id = :courseId
              and exists (
                  select registration.id
                  from FaceRegistration registration
                  where registration.student = enrollment.student
              )
            order by enrollment.student.id
            """)
    List<UUID> findFaceRegisteredStudentIdsByCourseId(@Param("courseId") UUID courseId);
}
