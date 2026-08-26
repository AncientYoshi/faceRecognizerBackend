package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

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
              AND (:studyYear IS NULL OR student.studyYear = :studyYear)
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
            @Param("studyYear") Integer studyYear,
            @Param("query") String query,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "department"})
    @Query(
            value = """
                    SELECT student
                    FROM Student student
                    WHERE student.department.id = :departmentId
                      AND (:studyYear IS NULL OR student.studyYear = :studyYear)
                      AND (
                            :query = ''
                            OR LOWER(student.studentNumber) LIKE :query
                            OR LOWER(student.user.email) LIKE :query
                            OR LOWER(student.user.firstName) LIKE :query
                            OR LOWER(student.user.lastName) LIKE :query
                          )
                    ORDER BY
                      CASE
                        WHEN FUNCTION('regexp_replace', student.studentNumber, '[^0-9]', '', 'g') = '' THEN 1
                        ELSE 0
                      END,
                      LENGTH(FUNCTION(
                              'regexp_replace',
                              FUNCTION('regexp_replace', student.studentNumber, '[^0-9]', '', 'g'),
                              '^0+',
                              '',
                              'g'
                      )),
                      FUNCTION(
                              'regexp_replace',
                              FUNCTION('regexp_replace', student.studentNumber, '[^0-9]', '', 'g'),
                              '^0+',
                              '',
                              'g'
                      ),
                      LOWER(student.studentNumber),
                      student.id
                    """,
            countQuery = """
                    SELECT COUNT(student)
                    FROM Student student
                    WHERE student.department.id = :departmentId
                      AND (:studyYear IS NULL OR student.studyYear = :studyYear)
                      AND (
                            :query = ''
                            OR LOWER(student.studentNumber) LIKE :query
                            OR LOWER(student.user.email) LIKE :query
                            OR LOWER(student.user.firstName) LIKE :query
                            OR LOWER(student.user.lastName) LIKE :query
                          )
                    """
    )
    Page<Student> searchDepartmentStudentsByStudentNumber(
            @Param("departmentId") UUID departmentId,
            @Param("studyYear") Integer studyYear,
            @Param("query") String query,
            Pageable pageable
    );
}
