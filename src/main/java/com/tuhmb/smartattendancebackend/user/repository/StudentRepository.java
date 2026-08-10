package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByStudentNumberIgnoreCase(String studentNumber);

    Optional<Student> findByStudentNumberIgnoreCase(String studentNumber);

    Optional<Student> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long countByDepartmentId(UUID departmentId);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("""
            SELECT student
            FROM Student student
            WHERE student.department.id = :departmentId
              AND (
                    :query = ''
                    OR LOWER(student.studentNumber) LIKE :query
                    OR LOWER(student.user.email) LIKE :query
                    OR LOWER(student.user.firstName) LIKE :query
                    OR LOWER(student.user.lastName) LIKE :query
                  )
            """)
    Page<Student> searchDepartmentStudents(
            @Param("departmentId") UUID departmentId,
            @Param("query") String query,
            Pageable pageable
    );
}
