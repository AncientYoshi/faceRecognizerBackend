package com.tuhmb.smartattendancebackend.dashboard.api;

import com.tuhmb.smartattendancebackend.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse admin() {
        return dashboardService.adminDashboard();
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherDashboardResponse teacher(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.teacherDashboard(jwt);
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboardResponse student(
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return dashboardService.studentDashboard(date, jwt);
    }
}
