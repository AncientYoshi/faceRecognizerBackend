package com.tuhmb.smartattendancebackend.timetable.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimetableRequest(

        @NotNull(message = "courseId is required")
        UUID courseId,

        @NotNull(message = "dayOfWeek is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        LocalTime endTime,

        @Min(value = 1, message = "rollCallCount must be at least 1")
        @Max(value = 6, message = "rollCallCount must not exceed 6")
        Integer rollCallCount,

        @Size(max = 100, message = "room must not exceed 100 characters")
        String room,

        LocalDate effectiveFrom,

        LocalDate effectiveTo,

        Boolean active
) {
}
