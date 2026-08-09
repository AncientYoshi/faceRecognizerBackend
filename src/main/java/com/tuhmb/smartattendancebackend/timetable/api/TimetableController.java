package com.tuhmb.smartattendancebackend.timetable.api;

import com.tuhmb.smartattendancebackend.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/timetables")
@SecurityRequirement(name = "bearerAuth")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(
            TimetableService timetableService
    ) {
        this.timetableService = timetableService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TimetableResponse> create(
            @Valid @RequestBody TimetableRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(timetableService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Page<TimetableResponse> search(
            @RequestParam(required = false)
            UUID courseId,

            @RequestParam(required = false)
            DayOfWeek dayOfWeek,

            @RequestParam(required = false)
            LocalDate onDate,

            @RequestParam(defaultValue = "true")
            Boolean active,

            @PageableDefault(
                    size = 20,
                    sort = {"dayOrder", "startTime"}
            )
            Pageable pageable
    ) {
        return timetableService.search(
                courseId,
                dayOfWeek,
                onDate,
                active,
                pageable
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public TimetableResponse getById(
            @PathVariable UUID id
    ) {
        return timetableService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public TimetableResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TimetableRequest request
    ) {
        return timetableService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id
    ) {
        timetableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
