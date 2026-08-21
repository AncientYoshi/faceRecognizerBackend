package com.tuhmb.smartattendancebackend.attendance.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceVerificationResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.face.client.FaceAiClient;
import com.tuhmb.smartattendancebackend.face.client.VerifyFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.exception.AiServiceException;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.face.service.ImageUploadValidator;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class AttendanceVerificationService {

    private final StudentRepository studentRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FaceRegistrationRepository faceRegistrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final FaceAiClient faceAiClient;
    private final ImageUploadValidator imageValidator;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;

    public AttendanceVerificationService(
            StudentRepository studentRepository,
            AttendanceSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            FaceRegistrationRepository faceRegistrationRepository,
            AttendanceRepository attendanceRepository,
            FaceAiClient faceAiClient,
            ImageUploadValidator imageValidator,
            TransactionTemplate transactionTemplate,
            AuditService auditService
    ) {
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.faceRegistrationRepository = faceRegistrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.faceAiClient = faceAiClient;
        this.imageValidator = imageValidator;
        this.transactionTemplate = transactionTemplate;
        this.auditService = auditService;
    }

    public AttendanceVerificationResponse verify(UUID sessionId, MultipartFile image, Jwt jwt) {
        imageValidator.validate(image);
        UUID userId = parseSubject(jwt);
        VerificationContext context = transactionTemplate.execute(
                status -> prepareVerification(userId, sessionId, Instant.now())
        );
        if (context == null) {
            throw new IllegalStateException("Verification context could not be created");
        }
        if (context.existingAttendance() != null) {
            return AttendanceVerificationResponse.alreadyRecorded(context.existingAttendance());
        }

        VerifyFaceAiResponse aiResponse = faceAiClient.verify(context.studentId().toString(), image);
        validateAiResponse(aiResponse);
        if (!aiResponse.matched()) {
            return AttendanceVerificationResponse.notMatched(aiResponse.similarity());
        }

        try {
            AttendanceVerificationResponse response = transactionTemplate.execute(
                    status -> recordAttendance(
                            context.studentId(),
                            context.sessionId(),
                            aiResponse.similarity(),
                            Instant.now()
                    )
            );
            if (response == null) {
                throw new IllegalStateException("Attendance record could not be created");
            }
            return response;
        } catch (DataIntegrityViolationException exception) {
            return attendanceRepository.findBySessionIdAndStudentId(context.sessionId(), context.studentId())
                    .map(AttendanceVerificationResponse::alreadyRecorded)
                    .orElseThrow(() -> new ConflictException(
                            "Attendance has already been recorded for this session"
                    ));
        }
    }

    private VerificationContext prepareVerification(UUID userId, UUID sessionId, Instant now) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found"));
        AttendanceSession session = findSession(sessionId);
        Attendance existing = attendanceRepository.findBySessionIdAndStudentId(session.getId(), student.getId())
                .orElse(null);
        if (existing != null) {
            return new VerificationContext(student.getId(), session.getId(), existing);
        }
        validateEligibility(student, session, now);
        return new VerificationContext(student.getId(), session.getId(), null);
    }

    private AttendanceVerificationResponse recordAttendance(
            UUID studentId,
            UUID sessionId,
            double similarity,
            Instant verifiedAt
    ) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
        AttendanceSession session = findSession(sessionId);
        validateEligibility(student, session, verifiedAt);
        Attendance attendance = new Attendance(
                student,
                session.getCourse(),
                session,
                BigDecimal.valueOf(similarity).setScale(5, RoundingMode.HALF_UP),
                verifiedAt
        );
        Attendance saved = attendanceRepository.saveAndFlush(attendance);
        auditService.recordForUser(
                student.getUser().getId(),
                AuditAction.ATTENDANCE_RECORDED,
                "Attendance",
                saved.getId(),
                "Session " + session.getId() + ", similarity " + saved.getSimilarityScore()
        );
        return AttendanceVerificationResponse.recorded(saved);
    }

    private void validateEligibility(Student student, AttendanceSession session, Instant now) {
        if (session.getStatus() != AttendanceSessionStatus.ACTIVE) {
            throw new ConflictException("Attendance session is not active");
        }
        if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
            throw new ConflictException("Attendance verification is outside the session time window");
        }
        if (!enrollmentRepository.existsByStudentIdAndCourseId(
                student.getId(),
                session.getCourse().getId()
        )) {
            throw new ConflictException("Student is not enrolled in this course");
        }
        if (!faceRegistrationRepository.existsByStudentId(student.getId())) {
            throw new ConflictException("Student must register a face before attendance verification");
        }
        if (attendanceRepository.existsBySessionIdAndStudentId(session.getId(), student.getId())) {
            throw new ConflictException("Attendance has already been recorded for this session");
        }
    }

    private void validateAiResponse(VerifyFaceAiResponse response) {
        if (response == null
                || !Double.isFinite(response.similarity())
                || response.similarity() < 0
                || response.similarity() > 1) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "ai_invalid_verification_response",
                    "AI face service returned an invalid similarity score"
            );
        }
    }

    private AttendanceSession findSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session was not found"));
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated student was not found");
        }
    }

    private record VerificationContext(UUID studentId, UUID sessionId, Attendance existingAttendance) {
    }
}
