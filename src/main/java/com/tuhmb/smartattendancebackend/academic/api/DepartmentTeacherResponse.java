package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;

import java.util.UUID;

public record DepartmentTeacherResponse(
        UUID teacherId,
        UUID userId,
        String employeeNumber,
        String email,
        String firstName,
        String lastName,
        String fullName,
        UUID departmentId,
        String departmentCode,
        String departmentName
) {
    public static DepartmentTeacherResponse from(Teacher teacher) {
        AppUser user = teacher.getUser();
        Department department = teacher.getDepartment();
        return new DepartmentTeacherResponse(
                teacher.getId(),
                user.getId(),
                teacher.getEmployeeNumber(),
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
