package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSessionResponse(
        UUID id,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID teacherId,
        UUID teacherUserId,
        String teacherName,
        LocalDate sessionDate,
        Instant startTime,
        Instant endTime,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AttendanceSessionResponse from(AttendanceSession session) {
        return new AttendanceSessionResponse(
                session.getId(),
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
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
