package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

    Optional<Teacher> findByEmployeeNumberIgnoreCase(String employeeNumber);

    Optional<Teacher> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long countByDepartmentId(UUID departmentId);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("""
            SELECT teacher
            FROM Teacher teacher
            WHERE teacher.department.id = :departmentId
              AND (
                    :query = ''
                    OR LOWER(teacher.employeeNumber) LIKE :query
                    OR LOWER(teacher.user.email) LIKE :query
                    OR LOWER(teacher.user.firstName) LIKE :query
                    OR LOWER(teacher.user.lastName) LIKE :query
                  )
            """)
    Page<Teacher> searchDepartmentTeachers(
            @Param("departmentId") UUID departmentId,
            @Param("query") String query,
            Pageable pageable
    );
}
