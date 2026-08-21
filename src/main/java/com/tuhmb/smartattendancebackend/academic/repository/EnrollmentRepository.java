package com.tuhmb.smartattendancebackend.academic.repository;

import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"course"})
    List<Enrollment> findAllByStudentIdOrderByCourseCode(UUID studentId);

    @EntityGraph(attributePaths = {"student.user"})
    @Query("""
            select enrollment
            from Enrollment enrollment
            where enrollment.course.id = :courseId
              and enrollment.student.user.enabled = true
            order by enrollment.student.user.email
            """)
    List<Enrollment> findEnabledStudentsByCourseId(@Param("courseId") UUID courseId);

    long countByCourseId(UUID courseId);

    long countByStudentId(UUID studentId);

    @Query("""
            select count(enrollment)
            from Enrollment enrollment
            where enrollment.course.id = :courseId
              and (
                    enrollment.student.studyYear is null
                    or enrollment.student.studyYear <> :studyYear
                  )
            """)
    long countStudentsOutsideStudyYear(
            @Param("courseId") UUID courseId,
            @Param("studyYear") Integer studyYear
    );

    @Query("""
            select count(enrollment)
            from Enrollment enrollment
            where enrollment.student.id = :studentId
              and lower(enrollment.course.semester) = lower(:semester)
              and lower(enrollment.course.academicYear) = lower(:academicYear)
            """)
    long countCurrentTermCourses(
            @Param("studentId") UUID studentId,
            @Param("semester") String semester,
            @Param("academicYear") String academicYear
    );

    @EntityGraph(attributePaths = {"student.user", "course"})
    @Query("""
            select enrollment
            from Enrollment enrollment
            where (:courseId is null or enrollment.course.id = :courseId)
              and (:teacherUserId is null or enrollment.course.teacher.user.id = :teacherUserId)
              and (:studyYear is null or enrollment.student.studyYear = :studyYear)
              and (
                    :query = ''
                    or lower(enrollment.student.studentNumber) like :query
                    or lower(enrollment.student.user.email) like :query
                    or lower(enrollment.student.user.firstName) like :query
                    or lower(enrollment.student.user.lastName) like :query
                  )
            """)
    Page<Enrollment> searchForStudentAttendanceReport(
            @Param("courseId") UUID courseId,
            @Param("teacherUserId") UUID teacherUserId,
            @Param("studyYear") Integer studyYear,
            @Param("query") String query,
            Pageable pageable
    );

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
