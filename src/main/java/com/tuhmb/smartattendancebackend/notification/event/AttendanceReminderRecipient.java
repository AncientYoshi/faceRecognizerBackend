package com.tuhmb.smartattendancebackend.notification.event;

public record AttendanceReminderRecipient(
        String email,
        String firstName
) {
}
