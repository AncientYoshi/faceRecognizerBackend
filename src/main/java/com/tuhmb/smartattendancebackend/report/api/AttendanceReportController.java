package com.tuhmb.smartattendancebackend.report.api;

import com.tuhmb.smartattendancebackend.report.service.AttendanceReportFilter;
import com.tuhmb.smartattendancebackend.report.service.AttendanceReportService;
import com.tuhmb.smartattendancebackend.report.service.ReportFile;
import com.tuhmb.smartattendancebackend.report.service.StudentAttendancePercentageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/reports/attendance")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AttendanceReportController {

    private final AttendanceReportService reportService;
    private final StudentAttendancePercentageService percentageService;

    public AttendanceReportController(
            AttendanceReportService reportService,
            StudentAttendancePercentageService percentageService
    ) {
        this.reportService = reportService;
        this.percentageService = percentageService;
    }

    @GetMapping("/students")
    public StudentAttendancePercentageReportResponse studentPercentages(
            @RequestParam(defaultValue = "WEEK") AttendanceReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @Min(1) @Max(6) Integer studyYear,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return percentageService.report(period, date, courseId, studyYear, query, page, size, jwt);
    }

    @GetMapping("/students/export/pdf")
    public ResponseEntity<byte[]> studentPercentagesPdf(
            @RequestParam(defaultValue = "WEEK") AttendanceReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @Min(1) @Max(6) Integer studyYear,
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return download(percentageService.exportPdf(period, date, courseId, studyYear, query, jwt));
    }

    @GetMapping("/students/export/excel")
    public ResponseEntity<byte[]> studentPercentagesExcel(
            @RequestParam(defaultValue = "WEEK") AttendanceReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @Min(1) @Max(6) Integer studyYear,
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return download(percentageService.exportExcel(period, date, courseId, studyYear, query, jwt));
    }

    @GetMapping
    public AttendanceReportResponse report(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reportService.search(filter(courseId, studentId, departmentId, month, from, to), page, size, jwt);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return download(reportService.exportPdf(
                filter(courseId, studentId, departmentId, month, from, to),
                jwt
        ));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return download(reportService.exportExcel(
                filter(courseId, studentId, departmentId, month, from, to),
                jwt
        ));
    }

    private AttendanceReportFilter filter(
            UUID courseId,
            UUID studentId,
            UUID departmentId,
            String month,
            LocalDate from,
            LocalDate to
    ) {
        if (month != null && (!month.isBlank()) && (from != null || to != null)) {
            throw new IllegalArgumentException("Use either 'month' or 'from'/'to', not both");
        }
        if (month != null && !month.isBlank()) {
            try {
                YearMonth yearMonth = YearMonth.parse(month);
                from = yearMonth.atDay(1);
                to = yearMonth.atEndOfMonth();
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("'month' must use YYYY-MM format");
            }
        }
        return new AttendanceReportFilter(courseId, studentId, departmentId, from, to);
    }

    private ResponseEntity<byte[]> download(ReportFile file) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .body(file.bytes());
    }
}
