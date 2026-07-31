package com.tuhmb.smartattendancebackend.academic.repository;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {

    Optional<Department> findByCodeIgnoreCase(String code);

    Optional<Department> findByNameIgnoreCase(String name);
}
