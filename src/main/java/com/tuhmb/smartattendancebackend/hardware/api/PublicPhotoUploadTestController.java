package com.tuhmb.smartattendancebackend.hardware.api;

import com.tuhmb.smartattendancebackend.hardware.service.PublicPhotoUploadTestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/upload")
@ConditionalOnProperty(prefix = "app.photo-upload-test", name = "enabled", havingValue = "true")
public class PublicPhotoUploadTestController {

    private final PublicPhotoUploadTestService uploadService;

    public PublicPhotoUploadTestController(PublicPhotoUploadTestService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public PublicPhotoUploadTestResponse upload(HttpServletRequest request) throws IOException {
        return uploadService.save(request.getInputStream(), request.getContentLengthLong());
    }
}
