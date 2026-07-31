package com.tuhmb.smartattendancebackend.face.client;

import com.tuhmb.smartattendancebackend.face.exception.AiServiceException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class HttpFaceAiClient implements FaceAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpFaceAiClient(RestClient faceAiRestClient, ObjectMapper objectMapper) {
        this.restClient = faceAiRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RegisterFaceAiResponse register(String studentId, MultipartFile image) {
        return post("/faces/register", studentId, image, RegisterFaceAiResponse.class);
    }

    @Override
    public VerifyFaceAiResponse verify(String studentId, MultipartFile image) {
        return post("/faces/verify", studentId, image, VerifyFaceAiResponse.class);
    }

    private <T> T post(String path, String studentId, MultipartFile image, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("studentId", studentId);
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(mediaType(image));
        imageHeaders.setContentDisposition(ContentDisposition.formData()
                .name("image")
                .filename(safeFilename(image))
                .build());
        body.add(
                "image",
                new HttpEntity<>(
                        new NamedByteArrayResource(readBytes(image), safeFilename(image)),
                        imageHeaders
                )
        );
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new AiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "ai_empty_response",
                        "AI face service returned an empty response"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapResponseError(exception);
        } catch (ResourceAccessException exception) {
            throw new AiServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ai_service_unavailable",
                    "AI face service is unavailable",
                    exception
            );
        }
    }

    private AiServiceException mapResponseError(RestClientResponseException exception) {
        HttpStatus downstreamStatus = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus responseStatus = downstreamStatus != null && downstreamStatus.is4xxClientError()
                ? downstreamStatus
                : HttpStatus.SERVICE_UNAVAILABLE;
        String code = "ai_service_error";
        String message = "AI face service rejected the request";
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode error = body.path("error");
            code = error.path("code").asText(code);
            message = error.path("message").asText(message);
        } catch (RuntimeException ignored) {
            // Preserve the stable fallback when the downstream body is not valid JSON.
        }
        return new AiServiceException(responseStatus, code, message, exception);
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new AiServiceException(
                    HttpStatus.BAD_REQUEST,
                    "image_read_failed",
                    "Face image could not be read",
                    exception
            );
        }
    }

    private MediaType mediaType(MultipartFile image) {
        return MediaType.parseMediaType(image.getContentType());
    }

    private String safeFilename(MultipartFile image) {
        String filename = image.getOriginalFilename();
        return filename == null || filename.isBlank() ? "face-image" : filename;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
