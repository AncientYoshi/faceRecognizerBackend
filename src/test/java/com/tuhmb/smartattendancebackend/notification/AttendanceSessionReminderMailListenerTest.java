package com.tuhmb.smartattendancebackend.notification;

import com.tuhmb.smartattendancebackend.notification.config.MailNotificationProperties;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceReminderRecipient;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceSessionStartedEvent;
import com.tuhmb.smartattendancebackend.notification.service.AttendanceSessionReminderMailListener;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceSessionReminderMailListenerTest {

    @Test
    void sendsAnIndividualFaceVerificationReminderToEveryRecipient() {
        RecordingMailSender mailSender = new RecordingMailSender();
        AttendanceSessionReminderMailListener listener = new AttendanceSessionReminderMailListener(
                mailSender,
                new MailNotificationProperties(
                        true,
                        "no-reply@example.com",
                        "https://frontend.example.com/"
                ),
                "Asia/Yangon"
        );
        UUID sessionId = UUID.randomUUID();
        AttendanceSessionStartedEvent event = new AttendanceSessionStartedEvent(
                sessionId,
                "CSE-101",
                "Artificial Intelligence",
                "Grace Teacher",
                LocalDate.of(2026, 8, 16),
                Instant.parse("2026-08-16T03:00:00Z"),
                Instant.parse("2026-08-16T04:00:00Z"),
                List.of(
                        new AttendanceReminderRecipient("alice@example.com", "Alice"),
                        new AttendanceReminderRecipient("bob@example.com", "Bob")
                )
        );

        listener.sendReminders(event);

        List<SimpleMailMessage> messages = mailSender.messages();
        assertThat(messages).extracting(message -> message.getTo()[0])
                .containsExactly("alice@example.com", "bob@example.com");
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
            assertThat(message.getSubject()).isEqualTo("Attendance verification started: CSE-101");
            assertThat(message.getText())
                    .contains("Artificial Intelligence")
                    .contains("Grace Teacher")
                    .contains("https://frontend.example.com/student/scan/" + sessionId);
        });
    }

    private static final class RecordingMailSender extends JavaMailSenderImpl {

        private final List<SimpleMailMessage> messages = new ArrayList<>();

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            messages.add(new SimpleMailMessage(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            messages.addAll(Arrays.stream(simpleMessages).map(SimpleMailMessage::new).toList());
        }

        List<SimpleMailMessage> messages() {
            return List.copyOf(messages);
        }
    }
}
