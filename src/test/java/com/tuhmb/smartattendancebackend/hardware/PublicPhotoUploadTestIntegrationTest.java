package com.tuhmb.smartattendancebackend.hardware;

import com.tuhmb.smartattendancebackend.audit.repository.AuditLogRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.photo-upload-test.enabled=true",
        "app.photo-upload-test.directory=/tmp/smart-attendance-public-upload-test",
        "app.photo-upload-test.max-bytes=1024"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPhotoUploadTestIntegrationTest {

    private static final Path CAPTURED_FILE = Path.of(
            "/tmp/smart-attendance-public-upload-test/captured.jpg"
    );

    @Autowired MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void removeCapturedFile() throws Exception {
        Files.deleteIfExists(CAPTURED_FILE);
    }

    @Test
    void acceptsRawJpegWithoutAuthenticationAndSavesLatestCapture() throws Exception {
        byte[] jpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46,
                (byte) 0xFF, (byte) 0xD9
        };

        mockMvc.perform(post("/upload")
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(jpeg))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.receivedBytes").value(jpeg.length))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.savedFile").value(CAPTURED_FILE.toString()));

        org.assertj.core.api.Assertions.assertThat(Files.readAllBytes(CAPTURED_FILE))
                .containsExactly(jpeg);
    }

    @Test
    void rejectsBodyThatIsNotJpeg() throws Exception {
        mockMvc.perform(post("/upload")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("not-a-jpeg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_jpeg"));
    }
}
