package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Student;

import java.util.UUID;

public record DepartmentStudentResponse(
        UUID studentId,
        UUID userId,
        String studentNumber,
        String email,
        String firstName,
        String lastName,
        String fullName,
        UUID departmentId,
        String departmentCode,
        String departmentName
) {
    public static DepartmentStudentResponse from(Student student) {
        AppUser user = student.getUser();
        Department department = student.getDepartment();
        return new DepartmentStudentResponse(
                student.getId(),
                user.getId(),
                student.getStudentNumber(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFirstName() + " " + user.getLastName(),
                department.getId(),
                department.getCode(),
                department.getName()
        );
    }
}
