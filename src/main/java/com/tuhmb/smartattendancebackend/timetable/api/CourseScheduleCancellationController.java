package com.tuhmb.smartattendancebackend.timetable.api;

import com.tuhmb.smartattendancebackend.timetable.service.CourseScheduleCancellationService;
import com.tuhmb.smartattendancebackend.timetable.service.CourseScheduleCancellationService.Context;
import com.tuhmb.smartattendancebackend.timetable.service.CourseScheduleCancellationService.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/courses/{courseId}/schedule-cancellations")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class CourseScheduleCancellationController {
    public enum Scope { TODAY, FUTURE }
    public record Request(@NotNull Scope scope, LocalDate fromDate,
                          @NotBlank @Size(max = 500) String reason) {}

    private final CourseScheduleCancellationService service;
    public CourseScheduleCancellationController(CourseScheduleCancellationService service) {
        this.service = service;
    }

    @GetMapping
    public Context list(@PathVariable UUID courseId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(courseId, jwt);
    }

    @PostMapping
    public Result cancel(@PathVariable UUID courseId, @Valid @RequestBody Request request,
                         @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(courseId, request, jwt);
    }
}
