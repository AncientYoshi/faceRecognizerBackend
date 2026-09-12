package com.tuhmb.smartattendancebackend.notification.service;

import com.tuhmb.smartattendancebackend.notification.config.MailNotificationProperties;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceReminderRecipient;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceSessionStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(prefix = "app.notifications.mail", name = "enabled", havingValue = "true")
public class AttendanceSessionReminderMailListener {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSessionReminderMailListener.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final JavaMailSender mailSender;
    private final MailNotificationProperties properties;
    private final ZoneId zoneId;
    private final AttendanceSessionRepository sessions;

    public AttendanceSessionReminderMailListener(
            JavaMailSender mailSender,
            MailNotificationProperties properties,
            AttendanceSessionRepository sessions,
            @org.springframework.beans.factory.annotation.Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.sessions = sessions;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Async("attendanceMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendReminders(AttendanceSessionStartedEvent event) {
        int sent = 0;
        for (AttendanceReminderRecipient recipient : event.recipients()) {
            // A reminder may have been queued before the teacher cancelled the class.
            if (!sessions.existsByIdAndStatus(event.sessionId(), AttendanceSessionStatus.ACTIVE)) break;
            try {
                mailSender.send(message(event, recipient));
                sent++;
            } catch (MailException | IllegalArgumentException exception) {
                log.error(
                        "Could not send attendance reminder for session {} to {}: {}",
                        event.sessionId(),
                        recipient.email(),
                        exception.getMessage()
                );
            }
        }
        log.info(
                "Sent {}/{} attendance reminder emails for session {}",
                sent,
                event.recipients().size(),
                event.sessionId()
        );
    }

    private SimpleMailMessage message(
            AttendanceSessionStartedEvent event,
            AttendanceReminderRecipient recipient
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (properties.from() != null && !properties.from().isBlank()) {
            message.setFrom(properties.from().trim());
        }
        message.setTo(recipient.email());
        message.setSubject("Attendance verification started: " + event.courseCode());
        message.setText("""
                Hello %s,

                Attendance verification has started for your class.

                Course: %s - %s
                Teacher: %s
                Date: %s
                Verification window: %s - %s

                Verify your attendance using your registered face:
                %s/student/scan/%s

                Smart Attendance Management
                """.formatted(
                recipient.firstName(),
                event.courseCode(),
                event.courseName(),
                event.teacherName(),
                event.sessionDate(),
                TIME_FORMAT.format(event.startTime().atZone(zoneId)),
                TIME_FORMAT.format(event.endTime().atZone(zoneId)),
                frontendBaseUrl(),
                event.sessionId()
        ));
        return message;
    }

    private String frontendBaseUrl() {
        String value = properties.frontendBaseUrl().trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
