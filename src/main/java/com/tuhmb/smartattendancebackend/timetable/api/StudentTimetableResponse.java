package com.tuhmb.smartattendancebackend.timetable.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentTimetableResponse(
        UUID studentId,
        LocalDate today,
        LocalDate weekStart,
        LocalDate weekEnd,
        String timeZone,
        List<StudentTimetableEntryResponse> entries
) {
}
