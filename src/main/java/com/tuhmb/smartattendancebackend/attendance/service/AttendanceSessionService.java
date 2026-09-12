package com.tuhmb.smartattendancebackend.attendance.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.service.AcademicAccessService;
import com.tuhmb.smartattendancebackend.academic.service.CourseService;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionRequest;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceSessionResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.notification.service.AttendanceSessionReminderPublisher;
import com.tuhmb.smartattendancebackend.timetable.service.CourseSchedulePolicy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@Service
public class AttendanceSessionService {

    private final AttendanceSessionRepository sessionRepository;
    private final CourseService courseService;
    private final AcademicAccessService accessService;
    private final AuditService auditService;
    private final AttendanceSessionReminderPublisher reminderPublisher;
    private final CourseSchedulePolicy schedulePolicy;

    public AttendanceSessionService(
            AttendanceSessionRepository sessionRepository,
            CourseService courseService,
            AcademicAccessService accessService,
            AuditService auditService,
            AttendanceSessionReminderPublisher reminderPublisher,
            CourseSchedulePolicy schedulePolicy
    ) {
        this.sessionRepository = sessionRepository;
        this.courseService = courseService;
        this.accessService = accessService;
        this.auditService = auditService;
        this.reminderPublisher = reminderPublisher;
        this.schedulePolicy = schedulePolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceSessionResponse> search(
            UUID courseId,
            UUID teacherId,
            LocalDate date,
            AttendanceSessionStatus status,
            int page,
            int size
    ) {
        Specification<AttendanceSession> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (courseId != null) {
                predicates.add(builder.equal(root.get("course").get("id"), courseId));
            }
            if (teacherId != null) {
                predicates.add(builder.equal(root.get("teacher").get("id"), teacherId));
            }
            if (date != null) {
                predicates.add(builder.equal(root.get("sessionDate"), date));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<AttendanceSession> result = sessionRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))
        );
        return PageResponse.from(result.map(AttendanceSessionResponse::from));
    }

    @Transactional(readOnly = true)
    public AttendanceSessionResponse get(UUID id) {
        return AttendanceSessionResponse.from(findSession(id));
    }

    @Transactional
    public AttendanceSessionResponse create(
            AttendanceSessionRequest request,
            UUID idempotencyKey,
            Jwt jwt
    ) {
        Course course = schedulePolicy.lockCourse(request.courseId());
        accessService.requireCourseTeacherOrAdmin(course, jwt);
        schedulePolicy.requireOpen(course.getId(), request.sessionDate());
        if (idempotencyKey != null) {
            AttendanceSession existing = sessionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                accessService.requireCourseTeacherOrAdmin(existing.getCourse(), jwt);
                requireSameCreateRequest(existing, request);
                return AttendanceSessionResponse.from(existing);
            }
        }
        validateDailyRollCallLimit(course, request.sessionDate(), request.rollCallCount(), null);
        AttendanceSession session = new AttendanceSession(
                course,
                course.getTeacher(),
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                request.rollCallCount()
        );
        session.assignIdempotencyKey(idempotencyKey);
        session.assignRoom(request.room());
        return AttendanceSessionResponse.from(sessionRepository.saveAndFlush(session));
    }

    @Transactional
    public AttendanceSessionResponse update(UUID id, AttendanceSessionRequest request, Jwt jwt) {
        AttendanceSession session = findAuthorizedSession(id, jwt);
        schedulePolicy.requireOpen(session.getCourse().getId(), request.sessionDate());
        if (!session.getCourse().getId().equals(request.courseId())) {
            throw new ConflictException("An attendance session cannot be moved to another course");
        }
        validateDailyRollCallLimit(
                session.getCourse(), request.sessionDate(), request.rollCallCount(), session.getId()
        );
        session.updateSchedule(
                request.sessionDate(), request.startTime(), request.endTime(), request.rollCallCount()
        );
        session.assignRoom(request.room());
        auditService.record(AuditAction.UPDATE, "AttendanceSession", id, "Attendance session updated");
        return AttendanceSessionResponse.from(session);
    }

    @Transactional
    public AttendanceSessionResponse start(UUID id, Jwt jwt) {
        AttendanceSession session = findAuthorizedSession(id, jwt);
        schedulePolicy.requireOpen(session.getCourse().getId(), session.getSessionDate());
        session.start();
        reminderPublisher.publish(session);
        return AttendanceSessionResponse.from(session);
    }

    @Transactional
    public AttendanceSessionResponse close(UUID id, Jwt jwt) {
        AttendanceSession session = findAuthorizedSession(id, jwt);
        session.close();
        return AttendanceSessionResponse.from(session);
    }

    @Transactional
    public AttendanceSessionResponse cancel(UUID id, Jwt jwt) {
        AttendanceSession session = findAuthorizedSession(id, jwt);
        session.cancel();
        auditService.record(AuditAction.UPDATE, "AttendanceSession", id, "Attendance session cancelled");
        return AttendanceSessionResponse.from(session);
    }

    @Transactional
    public void delete(UUID id, Jwt jwt) {
        AttendanceSession session = findAuthorizedSession(id, jwt);
        if (session.getStatus() != AttendanceSessionStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled attendance sessions can be deleted");
        }
        sessionRepository.delete(session);
        auditService.record(AuditAction.DELETE, "AttendanceSession", id, "Attendance session deleted");
    }

    private AttendanceSession findAuthorizedSession(UUID id, Jwt jwt) {
        AttendanceSession session = findSession(id);
        schedulePolicy.lockCourse(session.getCourse().getId());
        schedulePolicy.refresh(session);
        accessService.requireCourseTeacherOrAdmin(session.getCourse(), jwt);
        return session;
    }

    private AttendanceSession findSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session was not found"));
    }

    private void validateDailyRollCallLimit(
            Course course,
            LocalDate date,
            int requestedRollCalls,
            UUID excludedSessionId
    ) {
        if (course.getStudyYear() == null) {
            throw new ConflictException("Course study year must be assigned before scheduling attendance");
        }
        long scheduled = sessionRepository.sumCohortRollCallsForDate(
                course.getDepartment().getId(),
                course.getStudyYear(),
                date,
                AttendanceSessionStatus.CANCELLED,
                excludedSessionId
        );
        if (scheduled + requestedRollCalls > 6) {
            throw new ConflictException(
                    "A department study-year cohort cannot have more than 6 roll calls per day"
            );
        }
    }

    private void requireSameCreateRequest(
            AttendanceSession existing,
            AttendanceSessionRequest request
    ) {
        boolean sameRequest = existing.getCourse().getId().equals(request.courseId())
                && existing.getSessionDate().equals(request.sessionDate())
                && existing.getStartTime().equals(request.startTime())
                && existing.getEndTime().equals(request.endTime())
                && existing.getRollCallCount() == request.rollCallCount()
                && Objects.equals(existing.getAssignedRoom(), AttendanceSession.normalizeRoom(request.room()));
        if (!sameRequest) {
            throw new ConflictException("Idempotency-Key was already used for a different request");
        }
    }
}
