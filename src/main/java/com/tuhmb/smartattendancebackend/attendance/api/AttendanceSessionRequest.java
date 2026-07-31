package com.tuhmb.smartattendancebackend.attendance.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSessionRequest(
        @NotNull UUID courseId,
        @NotNull LocalDate sessionDate,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}
