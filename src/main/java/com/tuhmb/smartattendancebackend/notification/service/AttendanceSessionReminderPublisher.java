package com.tuhmb.smartattendancebackend.notification.service;

import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.notification.config.MailNotificationProperties;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceReminderRecipient;
import com.tuhmb.smartattendancebackend.notification.event.AttendanceSessionStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AttendanceSessionReminderPublisher {

    private final EnrollmentRepository enrollmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MailNotificationProperties properties;

    public AttendanceSessionReminderPublisher(
            EnrollmentRepository enrollmentRepository,
            ApplicationEventPublisher eventPublisher,
            MailNotificationProperties properties
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    public void publish(AttendanceSession session) {
        if (!properties.enabled()) {
            return;
        }
        var recipients = enrollmentRepository.findEnabledStudentsByCourseId(session.getCourse().getId())
                .stream()
                .map(enrollment -> new AttendanceReminderRecipient(
                        enrollment.getStudent().getUser().getEmail(),
                        enrollment.getStudent().getUser().getFirstName()
                ))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        String teacherName = session.getTeacher().getUser().getFirstName()
                + " "
                + session.getTeacher().getUser().getLastName();
        eventPublisher.publishEvent(new AttendanceSessionStartedEvent(
                session.getId(),
                session.getCourse().getCode(),
                session.getCourse().getName(),
                teacherName,
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                recipients
        ));
    }
}
