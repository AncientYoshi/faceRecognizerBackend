package com.tuhmb.smartattendancebackend.report;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import com.tuhmb.smartattendancebackend.report.service.AttendanceReportData;
import com.tuhmb.smartattendancebackend.report.service.AttendanceReportFilter;
import com.tuhmb.smartattendancebackend.report.service.ExcelAttendanceReportExporter;
import com.tuhmb.smartattendancebackend.report.service.PdfAttendanceReportExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExporterTest {

    @Test
    void createsReadablePdfAndExcelReports() throws Exception {
        AttendanceReportData report = sampleReport();
        byte[] pdf = new PdfAttendanceReportExporter("Asia/Yangon").export(report);
        byte[] excel = new ExcelAttendanceReportExporter("Asia/Yangon").export(report);

        assertTrue(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertEquals(1, document.getNumberOfPages());
            assertTrue(text.contains("Smart Attendance - Attendance Report"));
            assertTrue(text.contains("STU-001"));
        }

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Summary", workbook.getSheetAt(0).getSheetName());
            assertEquals("Attendance Records", workbook.getSheetAt(1).getSheetName());
            assertEquals(1d, workbook.getSheet("Summary").getRow(4).getCell(1).getNumericCellValue());
            assertEquals("STU-001", workbook.getSheet("Attendance Records").getRow(1).getCell(1).getStringCellValue());
        }

        Path verificationDirectory = Path.of("target", "report-verification");
        Files.createDirectories(verificationDirectory);
        Files.write(verificationDirectory.resolve("attendance-report.pdf"), pdf);
        Files.write(verificationDirectory.resolve("attendance-report.xlsx"), excel);
    }

    private AttendanceReportData sampleReport() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-07-30T08:15:00Z");
        AttendanceResponse attendance = new AttendanceResponse(
                UUID.randomUUID(),
                studentId,
                UUID.randomUUID(),
                "STU-001",
                "Alice Student",
                courseId,
                "CSE-101",
                "Software Engineering",
                sessionId,
                verifiedAt,
                "PRESENT",
                new BigDecimal("0.93456"),
                verifiedAt
        );
        return new AttendanceReportData(
                1,
                1,
                BigDecimal.ONE.setScale(4),
                verifiedAt,
                new AttendanceReportFilter(
                        courseId,
                        studentId,
                        null,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31)
                ),
                List.of(attendance)
        );
    }
}
