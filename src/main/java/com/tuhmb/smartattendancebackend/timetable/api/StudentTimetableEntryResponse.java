package com.tuhmb.smartattendancebackend.timetable.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record StudentTimetableEntryResponse(
        UUID timetableId,
        UUID courseId,
        String courseCode,
        String courseName,
        String semester,
        String academicYear,
        UUID teacherId,
        String teacherName,
        DayOfWeek dayOfWeek,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int rollCallCount,
        String room,
        boolean today
) {
}
