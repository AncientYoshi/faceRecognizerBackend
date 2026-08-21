package com.tuhmb.smartattendancebackend.user.api;

import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 200) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotEmpty Set<RoleName> roles,
        @Size(max = 80) String studentNumber,
        @Min(1) @Max(6) Integer studyYear,
        @Size(max = 80) String employeeNumber
) {
}
