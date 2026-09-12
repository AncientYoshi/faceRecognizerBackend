package com.tuhmb.smartattendancebackend.timetable.service;

import com.tuhmb.smartattendancebackend.academic.service.AcademicAccessService;
import com.tuhmb.smartattendancebackend.academic.service.CourseService;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.timetable.api.CourseScheduleCancellationController.Request;
import com.tuhmb.smartattendancebackend.timetable.api.CourseScheduleCancellationController.Scope;
import com.tuhmb.smartattendancebackend.timetable.domain.CourseScheduleCancellation;
import com.tuhmb.smartattendancebackend.timetable.repository.CourseScheduleCancellationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class CourseScheduleCancellationService {
    public record Cancellation(UUID id, LocalDate fromDate, LocalDate toDate, String reason, Instant createdAt) {
        static Cancellation from(CourseScheduleCancellation c) {
            return new Cancellation(c.getId(), c.getFromDate(), c.getToDate(), c.getReason(), c.getCreatedAt());
        }
    }
    public record Context(LocalDate today, String timeZone, List<Cancellation> cancellations) {}
    public record Result(Cancellation cancellation, int cancelledSessions) {}

    private final CourseScheduleCancellationRepository cancellations;
    private final AttendanceSessionRepository sessions;
    private final CourseService courses;
    private final AcademicAccessService access;
    private final CourseSchedulePolicy policy;
    private final AuditService audit;
    private final ZoneId zone;

    public CourseScheduleCancellationService(CourseScheduleCancellationRepository cancellations,
            AttendanceSessionRepository sessions, CourseService courses, AcademicAccessService access,
            CourseSchedulePolicy policy, AuditService audit,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone) {
        this.cancellations = cancellations;
        this.sessions = sessions;
        this.courses = courses;
        this.access = access;
        this.policy = policy;
        this.audit = audit;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public Context list(UUID courseId, Jwt jwt) {
        access.requireCourseTeacherOrAdmin(courses.findCourse(courseId), jwt);
        return new Context(LocalDate.now(zone), zone.getId(), cancellations
                .findByCourseIdOrderByCreatedAtDesc(courseId).stream().map(Cancellation::from).toList());
    }

    public Result cancel(UUID courseId, Request request, Jwt jwt) {
        var course = policy.lockCourse(courseId);
        access.requireCourseTeacherOrAdmin(course, jwt);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = request.scope() == Scope.TODAY ? today : request.fromDate();
        if (from == null || from.isBefore(today)) {
            throw new IllegalArgumentException("Choose today or a future date");
        }
        LocalDate to = request.scope() == Scope.TODAY ? today : null;
        var cancellation = cancellations.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(c -> c.getFromDate().equals(from) && Objects.equals(c.getToDate(), to))
                .findFirst().orElseGet(() -> cancellations.saveAndFlush(new CourseScheduleCancellation(
                        course, from, to, request.reason().trim(), access.subject(jwt))));
        int count = 0;
        for (var session : sessions.findByCourseIdAndSessionDateGreaterThanEqual(courseId, from)) {
            if ((to == null || !session.getSessionDate().isAfter(to))
                    && (session.getStatus() == AttendanceSessionStatus.SCHEDULED
                    || session.getStatus() == AttendanceSessionStatus.ACTIVE)) {
                session.cancel();
                count++;
            }
        }
        audit.record(AuditAction.UPDATE, "CourseScheduleCancellation", cancellation.getId(),
                "Course " + course.getCode() + " cancelled from " + from
                        + (to == null ? " onwards" : " through " + to));
        return new Result(Cancellation.from(cancellation), count);
    }
}
