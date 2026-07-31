package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.service.AttendanceRecordService;
import com.tuhmb.smartattendancebackend.attendance.service.AttendanceVerificationService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/attendance")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

    private final AttendanceVerificationService verificationService;
    private final AttendanceRecordService recordService;

    public AttendanceController(
            AttendanceVerificationService verificationService,
            AttendanceRecordService recordService
    ) {
        this.verificationService = verificationService;
        this.recordService = recordService;
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public AttendanceVerificationResponse verify(
            @RequestParam UUID sessionId,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return verificationService.verify(sessionId, image, jwt);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public PageResponse<AttendanceResponse> search(
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return recordService.search(sessionId, courseId, studentId, page, size, jwt);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public AttendanceResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return recordService.get(id, jwt);
    }
}
