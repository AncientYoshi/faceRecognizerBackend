package com.tuhmb.smartattendancebackend.face.service;

import com.tuhmb.smartattendancebackend.face.exception.ImageValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class ImageUploadValidator {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ImageValidationException(
                    HttpStatus.BAD_REQUEST,
                    "image_required",
                    "A non-empty face image is required"
            );
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ImageValidationException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "image_too_large",
                    "Face image must not exceed 10 MB"
            );
        }
        if (image.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new ImageValidationException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_image_type",
                    "Face image must be JPEG, PNG, or WebP"
            );
        }
    }
}
