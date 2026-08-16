package com.tuhmb.smartattendancebackend.notification.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AttendanceSessionStartedEvent(
        UUID sessionId,
        String courseCode,
        String courseName,
        String teacherName,
        LocalDate sessionDate,
        Instant startTime,
        Instant endTime,
        List<AttendanceReminderRecipient> recipients
) {
    public AttendanceSessionStartedEvent {
        recipients = List.copyOf(recipients);
    }
}
