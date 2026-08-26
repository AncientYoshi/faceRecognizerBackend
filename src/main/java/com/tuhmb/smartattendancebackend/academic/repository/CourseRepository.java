package com.tuhmb.smartattendancebackend.academic.repository;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    Optional<Course> findByCodeIgnoreCase(String code);

    long countByDepartmentId(UUID departmentId);

    long countByTeacherId(UUID teacherId);

    List<Course> findByTeacherIdOrderByCode(UUID teacherId);

    List<Course> findByDepartmentIdAndStudyYearOrderByCode(UUID departmentId, Integer studyYear);
}
