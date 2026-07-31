package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByStudentNumberIgnoreCase(String studentNumber);

    Optional<Student> findByStudentNumberIgnoreCase(String studentNumber);

    Optional<Student> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long countByDepartmentId(UUID departmentId);
}
