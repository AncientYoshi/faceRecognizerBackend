package com.tuhmb.smartattendancebackend.academic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CourseRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 40) String semester,
        @NotBlank @Size(max = 20) String academicYear,
        @NotNull UUID departmentId,
        @NotNull UUID teacherId
) {
}
