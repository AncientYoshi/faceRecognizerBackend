package com.tuhmb.smartattendancebackend.face;

import com.tuhmb.smartattendancebackend.face.client.HttpFaceAiClient;
import com.tuhmb.smartattendancebackend.face.client.IdentifyFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.client.RegisterFaceAiResponse;
import com.tuhmb.smartattendancebackend.face.client.VerifyFaceAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFaceAiClientTest {

    private MockRestServiceServer server;
    private HttpFaceAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpFaceAiClient(
                builder.baseUrl("http://localhost:8000").build(),
                JsonMapper.shared()
        );
    }

    @Test
    void sendsTheExactFastApiMultipartContract() {
        server.expect(requestTo("http://localhost:8000/faces/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"studentId\"")))
                .andExpect(content().string(containsString("student-123")))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andRespond(withSuccess(
                        "{\"success\":true,\"embeddingId\":\"embedding-123\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("http://localhost:8000/faces/verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"studentId\"")))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andRespond(withSuccess(
                        "{\"matched\":true,\"similarity\":0.91}",
                        MediaType.APPLICATION_JSON
                ));
        UUID candidateId = UUID.fromString("65788c5d-629c-426d-986d-eb77f42b7895");
        server.expect(requestTo("http://localhost:8000/faces/identify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"candidateStudentIds\"")))
                .andExpect(content().string(containsString(candidateId.toString())))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andRespond(withSuccess(
                        """
                                {"matched":true,"studentId":"%s","similarity":0.95,
                                 "livenessPassed":true,"reason":"MATCHED"}
                                """.formatted(candidateId),
                        MediaType.APPLICATION_JSON
                ));

        RegisterFaceAiResponse registration = client.register("student-123", image());
        assertTrue(registration.success());
        assertEquals("embedding-123", registration.embeddingId());

        VerifyFaceAiResponse verification = client.verify("student-123", image());
        assertTrue(verification.matched());
        assertEquals(0.91, verification.similarity());

        IdentifyFaceAiResponse identification = client.identify(List.of(candidateId), image());
        assertTrue(identification.matched());
        assertEquals(candidateId, identification.studentId());
        assertEquals(0.95, identification.similarity());
        assertTrue(identification.livenessPassed());
        server.verify();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
                "image",
                "face.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );
    }
}
