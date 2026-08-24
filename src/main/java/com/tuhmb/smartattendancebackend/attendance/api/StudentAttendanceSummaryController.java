package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.service.StudentAttendanceSummaryService;
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
@RequestMapping("/students/me/attendance-summary")
@PreAuthorize("hasRole('STUDENT')")
@SecurityRequirement(name = "bearerAuth")
public class StudentAttendanceSummaryController {

    private final StudentAttendanceSummaryService summaryService;

    public StudentAttendanceSummaryController(StudentAttendanceSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    public StudentAttendanceSummaryResponse summary(
            @RequestParam(defaultValue = "ALL") StudentAttendancePeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return summaryService.summary(period, date, jwt);
    }
}
