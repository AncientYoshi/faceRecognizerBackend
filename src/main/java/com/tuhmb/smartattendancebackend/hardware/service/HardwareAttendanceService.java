package com.tuhmb.smartattendancebackend.hardware.service;

import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.face.client.FaceAiClient;
import com.tuhmb.smartattendancebackend.face.client.IdentifyFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.exception.AiServiceException;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.face.service.ImageUploadValidator;
import com.tuhmb.smartattendancebackend.hardware.api.HardwareAttendanceResponse;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HardwareAttendanceService {

    private final HardwareDeviceAuthenticationService deviceAuthenticationService;
    private final AttendanceSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final FaceRegistrationRepository faceRegistrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final FaceAiClient faceAiClient;
    private final ImageUploadValidator imageValidator;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;

    public HardwareAttendanceService(
            HardwareDeviceAuthenticationService deviceAuthenticationService,
            AttendanceSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            StudentRepository studentRepository,
            FaceRegistrationRepository faceRegistrationRepository,
            AttendanceRepository attendanceRepository,
            FaceAiClient faceAiClient,
            ImageUploadValidator imageValidator,
            TransactionTemplate transactionTemplate,
            AuditService auditService
    ) {
        this.deviceAuthenticationService = deviceAuthenticationService;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.faceRegistrationRepository = faceRegistrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.faceAiClient = faceAiClient;
        this.imageValidator = imageValidator;
        this.transactionTemplate = transactionTemplate;
        this.auditService = auditService;
    }

    public HardwareAttendanceResponse identify(
            String deviceId,
            String deviceKey,
            UUID sessionId,
            MultipartFile image
    ) {
        deviceAuthenticationService.authenticate(deviceId, deviceKey);
        imageValidator.validate(image);

        IdentificationContext context = transactionTemplate.execute(
                status -> prepareIdentification(sessionId, Instant.now())
        );
        if (context == null) {
            throw new IllegalStateException("Hardware identification context could not be created");
        }
        if (context.candidateStudentIds().isEmpty()) {
            return HardwareAttendanceResponse.failed("NO_REGISTERED_STUDENTS", 0);
        }

        IdentifyFaceAiResponse face = faceAiClient.identify(context.candidateStudentIds(), image);
        validateAiResponse(face, context.candidateStudentIds());
        if (!face.matched()) {
            return HardwareAttendanceResponse.failed(reasonOr(face.reason(), "FACE_NOT_RECOGNIZED"), face.similarity());
        }
        if (!face.livenessPassed()) {
            return HardwareAttendanceResponse.failed("LIVENESS_FAILED", face.similarity());
        }

        try {
            HardwareAttendanceResponse response = transactionTemplate.execute(
                    status -> recordOrReturnExisting(
                            context.sessionId(),
                            face.studentId(),
                            face.similarity(),
                            Instant.now()
                    )
            );
            if (response == null) {
                throw new IllegalStateException("Hardware attendance result could not be created");
            }
            return response;
        } catch (DataIntegrityViolationException exception) {
            HardwareAttendanceResponse response = transactionTemplate.execute(
                    status -> existingAttendanceResponse(
                            context.sessionId(),
                            face.studentId(),
                            face.similarity()
                    )
            );
            if (response == null) {
                throw new ConflictException("Attendance has already been recorded for this session");
            }
            return response;
        }
    }

    private IdentificationContext prepareIdentification(UUID sessionId, Instant now) {
        AttendanceSession session = findSession(sessionId);
        validateSession(session, now);
        List<UUID> candidates = enrollmentRepository.findFaceRegisteredStudentIdsByCourseId(
                session.getCourse().getId()
        );
        return new IdentificationContext(session.getId(), List.copyOf(candidates));
    }

    private HardwareAttendanceResponse recordOrReturnExisting(
            UUID sessionId,
            UUID studentId,
            double similarity,
            Instant verifiedAt
    ) {
        AttendanceSession session = findSession(sessionId);
        validateSession(session, verifiedAt);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Matched student was not found"));
        if (!enrollmentRepository.existsByStudentIdAndCourseId(studentId, session.getCourse().getId())) {
            throw invalidIdentification("AI face service returned a student outside the candidate list");
        }
        if (!faceRegistrationRepository.existsByStudentId(studentId)) {
            throw invalidIdentification("AI face service returned a student without a face registration");
        }

        return attendanceRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .map(existing -> verifiedResponse(existing, student, false, similarity, "ALREADY_VERIFIED"))
                .orElseGet(() -> saveAttendance(student, session, similarity, verifiedAt));
    }

    private HardwareAttendanceResponse saveAttendance(
            Student student,
            AttendanceSession session,
            double similarity,
            Instant verifiedAt
    ) {
        Attendance attendance = attendanceRepository.saveAndFlush(new Attendance(
                student,
                session.getCourse(),
                session,
                BigDecimal.valueOf(similarity).setScale(5, RoundingMode.HALF_UP),
                verifiedAt
        ));
        auditService.recordForUser(
                student.getUser().getId(),
                AuditAction.ATTENDANCE_RECORDED,
                "Attendance",
                attendance.getId(),
                "Hardware session " + session.getId() + ", similarity " + attendance.getSimilarityScore()
        );
        return verifiedResponse(attendance, student, true, similarity, "ATTENDANCE_VERIFIED");
    }

    private HardwareAttendanceResponse existingAttendanceResponse(
            UUID sessionId,
            UUID studentId,
            double similarity
    ) {
        Attendance attendance = attendanceRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ConflictException("Attendance has already been recorded for this session"));
        return verifiedResponse(attendance, attendance.getStudent(), false, similarity, "ALREADY_VERIFIED");
    }

    private HardwareAttendanceResponse verifiedResponse(
            Attendance attendance,
            Student student,
            boolean recorded,
            double similarity,
            String reason
    ) {
        return new HardwareAttendanceResponse(
                true,
                reason,
                student.getId(),
                student.getStudentNumber(),
                recorded,
                attendance.getId(),
                similarity
        );
    }

    private void validateSession(AttendanceSession session, Instant now) {
        if (session.getStatus() != AttendanceSessionStatus.ACTIVE) {
            throw new ConflictException("Attendance session is not active");
        }
        if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
            throw new ConflictException("Attendance identification is outside the session time window");
        }
    }

    private void validateAiResponse(IdentifyFaceAiResponse response, List<UUID> candidates) {
        if (response == null
                || !Double.isFinite(response.similarity())
                || response.similarity() < 0
                || response.similarity() > 1) {
            throw invalidIdentification("AI face service returned an invalid identification response");
        }
        Set<UUID> candidateSet = Set.copyOf(candidates);
        if (response.matched()
                && (response.studentId() == null || !candidateSet.contains(response.studentId()))) {
            throw invalidIdentification("AI face service returned a student outside the candidate list");
        }
    }

    private AiServiceException invalidIdentification(String message) {
        return new AiServiceException(
                HttpStatus.BAD_GATEWAY,
                "ai_invalid_identification_response",
                message
        );
    }

    private AttendanceSession findSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session was not found"));
    }

    private String reasonOr(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private record IdentificationContext(UUID sessionId, List<UUID> candidateStudentIds) {
    }
}
