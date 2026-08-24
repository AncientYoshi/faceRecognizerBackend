package com.tuhmb.smartattendancebackend.attendance.service;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.notification.service.AttendanceSessionReminderPublisher;
import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import com.tuhmb.smartattendancebackend.timetable.repository.TimetableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AttendanceSessionAutomationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSessionAutomationService.class);

    private final TimetableRepository timetableRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceSessionReminderPublisher reminderPublisher;
    private final ZoneId zoneId;
    private final boolean enabled;

    public AttendanceSessionAutomationService(
            TimetableRepository timetableRepository,
            AttendanceSessionRepository sessionRepository,
            AttendanceSessionReminderPublisher reminderPublisher,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone,
            @Value("${app.attendance.automation.enabled:true}") boolean enabled
    ) {
        this.timetableRepository = timetableRepository;
        this.sessionRepository = sessionRepository;
        this.reminderPublisher = reminderPublisher;
        this.zoneId = ZoneId.of(timeZone);
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.attendance.automation.interval:30s}",
            initialDelayString = "${app.attendance.automation.initial-delay:10s}"
    )
    @Transactional
    public void synchronize() {
        if (enabled) {
            synchronizeAt(Instant.now());
        }
    }

    @Transactional
    public void synchronizeAt(Instant now) {
        LocalDate today = now.atZone(zoneId).toLocalDate();
        materializeTimetableSessions(today, now);
        startDueSessions(now);
        closeDueSessions(now);
    }

    private void materializeTimetableSessions(LocalDate date, Instant now) {
        List<TimetableEntry> entries = timetableRepository.findActiveEntriesForDate(date.getDayOfWeek(), date);
        for (TimetableEntry entry : entries) {
            try {
                materializeTimetableSession(entry, date, now);
            } catch (IllegalStateException exception) {
                log.error(
                        "Skipped timetable {} attendance automation: {}",
                        entry.getId(),
                        exception.getMessage()
                );
            }
        }
    }

    private void materializeTimetableSession(TimetableEntry entry, LocalDate date, Instant now) {
        Instant start = date.atTime(entry.getStartTime()).atZone(zoneId).toInstant();
        Instant end = date.atTime(entry.getEndTime()).atZone(zoneId).toInstant();
        AttendanceSession existing = sessionRepository
                .findByTimetableEntryIdAndSessionDate(entry.getId(), date)
                .orElseGet(() -> sessionRepository
                        .findFirstByCourseIdAndSessionDateAndStartTimeAndEndTime(
                                entry.getCourse().getId(), date, start, end
                        )
                        .orElse(null));

        if (existing == null && !now.isBefore(end)) {
            log.warn(
                    "Did not backfill already-ended timetable {} on {}",
                    entry.getId(),
                    date
            );
            return;
        }

        if (existing != null) {
            existing.linkTimetableEntry(entry);
            if (existing.getStatus() == AttendanceSessionStatus.SCHEDULED) {
                validateDailyLimit(entry, date, existing.getId());
                existing.updateSchedule(date, start, end, entry.getRollCallCount());
            }
            return;
        }

        validateDailyLimit(entry, date, null);
        AttendanceSession generated = new AttendanceSession(
                entry.getCourse(),
                entry.getCourse().getTeacher(),
                date,
                start,
                end,
                entry.getRollCallCount()
        );
        generated.linkTimetableEntry(entry);
        sessionRepository.save(generated);
        log.info(
                "Generated attendance session for timetable {} on {}",
                entry.getId(),
                date
        );
    }

    private void startDueSessions(Instant now) {
        List<AttendanceSession> sessions = sessionRepository.findByStatusAndStartTimeLessThanEqual(
                AttendanceSessionStatus.SCHEDULED,
                now
        );
        for (AttendanceSession session : sessions) {
            session.start();
            if (!now.isBefore(session.getEndTime())) {
                session.close();
                log.info("Automatically closed missed session {}", session.getId());
            } else {
                reminderPublisher.publish(session);
                log.info("Automatically started attendance session {}", session.getId());
            }
        }
    }

    private void closeDueSessions(Instant now) {
        List<AttendanceSession> sessions = sessionRepository.findByStatusAndEndTimeLessThanEqual(
                AttendanceSessionStatus.ACTIVE,
                now
        );
        for (AttendanceSession session : sessions) {
            session.close();
            log.info("Automatically closed attendance session {}", session.getId());
        }
    }

    private void validateDailyLimit(TimetableEntry entry, LocalDate date, java.util.UUID excludedSessionId) {
        if (entry.getCourse().getStudyYear() == null) {
            throw new IllegalStateException(
                    "Course " + entry.getCourse().getId() + " needs a study year before session automation"
            );
        }
        long existingCalls = sessionRepository.sumCohortRollCallsForDate(
                entry.getCourse().getDepartment().getId(),
                entry.getCourse().getStudyYear(),
                date,
                AttendanceSessionStatus.CANCELLED,
                excludedSessionId
        );
        if (existingCalls + entry.getRollCallCount() > 6) {
            throw new IllegalStateException(
                    "Timetable automation would exceed 6 roll calls for department "
                            + entry.getCourse().getDepartment().getId()
                            + ", study year " + entry.getCourse().getStudyYear()
                            + " on " + date
            );
        }
    }
}
