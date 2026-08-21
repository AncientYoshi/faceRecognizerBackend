package com.tuhmb.smartattendancebackend.attendance.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSessionRequest(
        @NotNull UUID courseId,
        @NotNull LocalDate sessionDate,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull @Min(1) @Max(6) Integer rollCallCount
) {
}
