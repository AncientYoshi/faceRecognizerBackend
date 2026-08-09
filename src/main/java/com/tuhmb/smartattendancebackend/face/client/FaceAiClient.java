package com.tuhmb.smartattendancebackend.face.client;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FaceAiClient {

    RegisterFaceAiResponse register(String studentId, MultipartFile image);

    VerifyFaceAiResponse verify(String studentId, MultipartFile image);

    IdentifyFaceAiResponse identify(List<UUID> candidateStudentIds, MultipartFile image);
}
