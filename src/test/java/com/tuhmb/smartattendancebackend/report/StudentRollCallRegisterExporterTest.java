package com.tuhmb.smartattendancebackend.report;

import com.tuhmb.smartattendancebackend.report.api.AttendanceReportPeriod;
import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageResponse;
import com.tuhmb.smartattendancebackend.report.service.CourseRollCallRegister;
import com.tuhmb.smartattendancebackend.report.service.StudentAttendancePercentageExcelExporter;
import com.tuhmb.smartattendancebackend.report.service.StudentAttendancePercentagePdfExporter;
import com.tuhmb.smartattendancebackend.report.service.StudentAttendancePercentageReportData;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentRollCallRegisterExporterTest {

    @Test
    void exportsUniversityStyleTwelveCallRegisterWithNinePresent() throws Exception {
        StudentAttendancePercentageReportData report = report();
        byte[] pdf = new StudentAttendancePercentagePdfExporter().export(report);
        byte[] excel = new StudentAttendancePercentageExcelExporter().export(report);

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Technological University (Hmawbi)"));
            assertTrue(text.contains("VMC-11"));
            assertTrue(text.contains("75.00%"));
        }

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheet("IA-501");
            assertEquals("Technological University (Hmawbi)", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("VMC-11", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals(3d, sheet.getRow(5).getCell(15).getNumericCellValue());
            assertEquals(9d, sheet.getRow(5).getCell(16).getNumericCellValue());
            assertEquals(0.75d, sheet.getRow(5).getCell(17).getNumericCellValue());
        }

        Path directory = Path.of("target", "report-verification");
        Files.createDirectories(directory);
        Files.write(directory.resolve("university-roll-call-register.pdf"), pdf);
        Files.write(directory.resolve("university-roll-call-register.xlsx"), excel);
    }

    private StudentAttendancePercentageReportData report() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<CourseRollCallRegister.RollCallColumn> columns = new ArrayList<>();
        for (int week = 0; week < 4; week++) {
            UUID sessionId = UUID.randomUUID();
            LocalDate date = LocalDate.of(2026, 8, 3).plusWeeks(week);
            for (int call = 1; call <= 3; call++) {
                columns.add(new CourseRollCallRegister.RollCallColumn(
                        sessionId,
                        date,
                        date.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC),
                        call
                ));
            }
        }
        List<Boolean> presence = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            presence.add(index < 9);
        }
        CourseRollCallRegister.StudentRow student = new CourseRollCallRegister.StudentRow(
                studentId,
                "VMC-11",
                "Example Student",
                5,
                presence,
                12,
                9,
                3,
                new BigDecimal("75.00")
        );
        CourseRollCallRegister register = new CourseRollCallRegister(
                courseId,
                "IA-501",
                "Industrial Automation",
                "2026-2027",
                "FIRST",
                5,
                UUID.randomUUID(),
                "MC",
                "Mechatronics",
                columns,
                List.of(student)
        );
        StudentAttendancePercentageResponse summary = new StudentAttendancePercentageResponse(
                studentId,
                UUID.randomUUID(),
                "VMC-11",
                5,
                "Example Student",
                courseId,
                "IA-501",
                "Industrial Automation",
                12,
                9,
                3,
                new BigDecimal("75.00")
        );
        return new StudentAttendancePercentageReportData(
                AttendanceReportPeriod.MONTH,
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-31T12:00:00Z"),
                courseId,
                5,
                "",
                List.of(summary),
                List.of(register)
        );
    }
}
