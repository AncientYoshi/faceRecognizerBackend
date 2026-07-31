package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Course;

import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String code,
        String name,
        String semester,
        String academicYear,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        UUID teacherId,
        UUID teacherUserId,
        String teacherName,
        long enrollmentCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourseResponse from(Course course, long enrollmentCount) {
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getSemester(),
                course.getAcademicYear(),
                course.getDepartment().getId(),
                course.getDepartment().getCode(),
                course.getDepartment().getName(),
                course.getTeacher().getId(),
                course.getTeacher().getUser().getId(),
                course.getTeacher().getUser().getFirstName() + " " + course.getTeacher().getUser().getLastName(),
                enrollmentCount,
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
