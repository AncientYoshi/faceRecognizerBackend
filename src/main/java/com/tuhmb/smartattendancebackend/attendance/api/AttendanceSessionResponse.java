package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSessionResponse(
        UUID id,
        UUID timetableEntryId,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID teacherId,
        UUID teacherUserId,
        String teacherName,
        LocalDate sessionDate,
        Instant startTime,
        Instant endTime,
        int rollCallCount,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String room
) {
    public static AttendanceSessionResponse from(AttendanceSession session) {
        return new AttendanceSessionResponse(
                session.getId(),
                session.getTimetableEntry() == null ? null : session.getTimetableEntry().getId(),
                session.getCourse().getId(),
                session.getCourse().getCode(),
                session.getCourse().getName(),
                session.getTeacher().getId(),
                session.getTeacher().getUser().getId(),
                session.getTeacher().getUser().getFirstName()
                        + " "
                        + session.getTeacher().getUser().getLastName(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getRollCallCount(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getRoom()
        );
    }
}
