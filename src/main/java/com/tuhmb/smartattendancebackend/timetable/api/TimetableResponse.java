package com.tuhmb.smartattendancebackend.timetable.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimetableResponse(
        UUID id,
        UUID courseId,
        String courseCode,
        String courseName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String room,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
