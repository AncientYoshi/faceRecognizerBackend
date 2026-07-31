package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSessionStatus;
import com.tuhmb.smartattendancebackend.attendance.service.AttendanceSessionService;
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

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/attendance-sessions")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceSessionController {

    private final AttendanceSessionService sessionService;

    public AttendanceSessionController(AttendanceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public PageResponse<AttendanceSessionResponse> search(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) AttendanceSessionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return sessionService.search(courseId, teacherId, date, status, page, size);
    }

    @GetMapping("/{id}")
    public AttendanceSessionResponse get(@PathVariable UUID id) {
        return sessionService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceSessionResponse create(
            @Valid @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessionService.create(request, jwt);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSessionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessionService.update(id, request, jwt);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSessionResponse start(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.start(id, jwt);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSessionResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.close(id, jwt);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSessionResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.cancel(id, jwt);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        sessionService.delete(id, jwt);
    }
}
