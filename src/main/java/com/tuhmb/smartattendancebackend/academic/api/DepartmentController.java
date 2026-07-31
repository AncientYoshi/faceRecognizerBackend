package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.service.DepartmentService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/departments")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public PageResponse<DepartmentResponse> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return departmentService.search(query, page, size);
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable UUID id) {
        return departmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest request) {
        return departmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return departmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        departmentService.delete(id);
    }

    @PutMapping("/{departmentId}/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileAssignmentResponse assignStudent(
            @PathVariable UUID departmentId,
            @PathVariable UUID studentId
    ) {
        return departmentService.assignStudent(departmentId, studentId);
    }

    @DeleteMapping("/{departmentId}/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileAssignmentResponse unassignStudent(
            @PathVariable UUID departmentId,
            @PathVariable UUID studentId
    ) {
        return departmentService.unassignStudent(departmentId, studentId);
    }

    @PutMapping("/{departmentId}/teachers/{teacherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileAssignmentResponse assignTeacher(
            @PathVariable UUID departmentId,
            @PathVariable UUID teacherId
    ) {
        return departmentService.assignTeacher(departmentId, teacherId);
    }

    @DeleteMapping("/{departmentId}/teachers/{teacherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileAssignmentResponse unassignTeacher(
            @PathVariable UUID departmentId,
            @PathVariable UUID teacherId
    ) {
        return departmentService.unassignTeacher(departmentId, teacherId);
    }
}
