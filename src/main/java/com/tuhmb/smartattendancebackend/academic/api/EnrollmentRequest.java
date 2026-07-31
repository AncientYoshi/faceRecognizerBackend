package com.tuhmb.smartattendancebackend.academic.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollmentRequest(@NotNull UUID studentId) {
}
