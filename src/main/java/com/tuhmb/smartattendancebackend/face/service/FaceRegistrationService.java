package com.tuhmb.smartattendancebackend.face.service;

import com.tuhmb.smartattendancebackend.academic.service.AcademicAccessService;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.face.api.FaceRegistrationResponse;
import com.tuhmb.smartattendancebackend.face.client.FaceAiClient;
import com.tuhmb.smartattendancebackend.face.client.RegisterFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.domain.FaceRegistration;
import com.tuhmb.smartattendancebackend.face.exception.AiServiceException;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class FaceRegistrationService {

    private final StudentRepository studentRepository;
    private final FaceRegistrationRepository registrationRepository;
    private final FaceAiClient faceAiClient;
    private final ImageUploadValidator imageValidator;
    private final AcademicAccessService accessService;
    private final TransactionTemplate transactionTemplate;

    public FaceRegistrationService(
            StudentRepository studentRepository,
            FaceRegistrationRepository registrationRepository,
            FaceAiClient faceAiClient,
            ImageUploadValidator imageValidator,
            AcademicAccessService accessService,
            TransactionTemplate transactionTemplate
    ) {
        this.studentRepository = studentRepository;
        this.registrationRepository = registrationRepository;
        this.faceAiClient = faceAiClient;
        this.imageValidator = imageValidator;
        this.accessService = accessService;
        this.transactionTemplate = transactionTemplate;
    }

    public FaceRegistrationResponse register(UUID studentId, MultipartFile image, Jwt jwt) {
        accessService.requireStudentOwnerOrAdmin(studentId, jwt);
        Student student = findStudent(studentId);
        imageValidator.validate(image);
        RegisterFaceAiResponse aiResponse = faceAiClient.register(studentId.toString(), image);
        if (!aiResponse.success() || aiResponse.embeddingId() == null || aiResponse.embeddingId().isBlank()) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "ai_invalid_registration_response",
                    "AI face service did not confirm face registration"
            );
        }
        FaceRegistrationResponse response = transactionTemplate.execute(
                status -> saveRegistration(student.getId(), aiResponse.embeddingId())
        );
        if (response == null) {
            throw new IllegalStateException("Face registration could not be saved");
        }
        return response;
    }

    @Transactional(readOnly = true)
    public FaceRegistrationResponse get(UUID studentId, Jwt jwt) {
        accessService.requireStudentOwnerOrAdmin(studentId, jwt);
        return FaceRegistrationResponse.from(
                registrationRepository.findByStudentId(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student has no face registration"))
        );
    }

    private FaceRegistrationResponse saveRegistration(UUID studentId, String embeddingId) {
        Student student = findStudent(studentId);
        FaceRegistration registration = registrationRepository.findByStudentId(studentId)
                .orElseGet(() -> new FaceRegistration(student, embeddingId));
        registration.updateEmbeddingId(embeddingId);
        return FaceRegistrationResponse.from(registrationRepository.save(registration));
    }

    private Student findStudent(UUID id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
    }
}
