package com.tuhmb.smartattendancebackend.face.client;

import org.springframework.web.multipart.MultipartFile;

public interface FaceAiClient {

    RegisterFaceAiResponse register(String studentId, MultipartFile image);

    VerifyFaceAiResponse verify(String studentId, MultipartFile image);
}
