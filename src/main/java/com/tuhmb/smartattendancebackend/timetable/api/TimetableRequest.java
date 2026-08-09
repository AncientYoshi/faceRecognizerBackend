package com.tuhmb.smartattendancebackend.timetable.api;

import jakarta.validation.constraints.NotNull;
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

        @Size(max = 100, message = "room must not exceed 100 characters")
        String room,

        LocalDate effectiveFrom,

        LocalDate effectiveTo,

        Boolean active
) {
}