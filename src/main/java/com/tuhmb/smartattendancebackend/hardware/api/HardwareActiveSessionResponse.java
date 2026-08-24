package com.tuhmb.smartattendancebackend.hardware.api;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HardwareActiveSessionResponse(
        UUID sessionId,
        UUID timetableEntryId,
        UUID courseId,
        String courseCode,
        String courseName,
        String room,
        LocalDate sessionDate,
        Instant startTime,
        Instant endTime,
        int rollCallCount,
        String status
) {
    public static HardwareActiveSessionResponse from(AttendanceSession session) {
        return new HardwareActiveSessionResponse(
                session.getId(),
                session.getTimetableEntry() == null ? null : session.getTimetableEntry().getId(),
                session.getCourse().getId(),
                session.getCourse().getCode(),
                session.getCourse().getName(),
                session.getTimetableEntry() == null ? null : session.getTimetableEntry().getRoom(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getRollCallCount(),
                session.getStatus().name()
        );
    }
}
