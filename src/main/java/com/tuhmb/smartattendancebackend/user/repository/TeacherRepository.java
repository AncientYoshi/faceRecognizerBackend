package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

    Optional<Teacher> findByEmployeeNumberIgnoreCase(String employeeNumber);

    Optional<Teacher> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long countByDepartmentId(UUID departmentId);
}
