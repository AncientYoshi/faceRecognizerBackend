package com.tuhmb.smartattendancebackend.timetable.api;

import com.tuhmb.smartattendancebackend.timetable.service.StudentTimetableService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/students/me/timetable")
@SecurityRequirement(name = "bearerAuth")
public class StudentTimetableController {

    private final StudentTimetableService studentTimetableService;

    public StudentTimetableController(StudentTimetableService studentTimetableService) {
        this.studentTimetableService = studentTimetableService;
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTimetableResponse getMyTimetable(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return studentTimetableService.getWeek(jwt, weekStart);
    }
}
