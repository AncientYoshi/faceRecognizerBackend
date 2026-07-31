package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.service.EnrollmentService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/students")
@SecurityRequirement(name = "bearerAuth")
public class StudentCourseController {

    private final EnrollmentService enrollmentService;

    public StudentCourseController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/{studentId}/courses")
    @PreAuthorize("hasAnyRole('ADMIN','STUDENT')")
    public PageResponse<CourseResponse> listCourses(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return enrollmentService.listStudentCourses(studentId, page, size, jwt);
    }
}
