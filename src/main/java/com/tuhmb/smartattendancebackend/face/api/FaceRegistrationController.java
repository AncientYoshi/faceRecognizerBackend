package com.tuhmb.smartattendancebackend.face.api;

import com.tuhmb.smartattendancebackend.face.service.FaceRegistrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/students/{studentId}/face")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','STUDENT')")
public class FaceRegistrationController {

    private final FaceRegistrationService registrationService;

    public FaceRegistrationController(FaceRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceRegistrationResponse register(
            @PathVariable UUID studentId,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return registrationService.register(studentId, image, jwt);
    }

    @GetMapping
    public FaceRegistrationResponse get(
            @PathVariable UUID studentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return registrationService.get(studentId, jwt);
    }
}
