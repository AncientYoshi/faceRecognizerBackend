package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.service.CourseService;
import com.tuhmb.smartattendancebackend.academic.service.EnrollmentService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/courses")
@SecurityRequirement(name = "bearerAuth")
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public CourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public PageResponse<CourseResponse> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String academicYear,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return courseService.search(query, departmentId, teacherId, semester, academicYear, page, size);
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable UUID id) {
        return courseService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CourseRequest request) {
        return courseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return courseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        courseService.delete(id);
    }

    @PostMapping("/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(
            @PathVariable UUID courseId,
            @Valid @RequestBody EnrollmentRequest request
    ) {
        return enrollmentService.enroll(courseId, request.studentId());
    }

    @DeleteMapping("/{courseId}/enrollments/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable UUID courseId, @PathVariable UUID studentId) {
        enrollmentService.unenroll(courseId, studentId);
    }

    @GetMapping("/{courseId}/enrollments")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public PageResponse<EnrollmentResponse> listEnrollments(
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return enrollmentService.listCourse(courseId, page, size, jwt);
    }
}
