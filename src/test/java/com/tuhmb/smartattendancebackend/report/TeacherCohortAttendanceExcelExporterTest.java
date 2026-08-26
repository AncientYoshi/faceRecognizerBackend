package com.tuhmb.smartattendancebackend.report;

import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;
import com.tuhmb.smartattendancebackend.report.service.TeacherCohortAttendanceExcelExporter;
import com.tuhmb.smartattendancebackend.report.service.TeacherCohortAttendanceReportData;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherCohortAttendanceExcelExporterTest {

    @Test
    void createsReferenceStyleCohortMatrixWithDynamicCourseAverage() throws Exception {
        TeacherCohortAttendanceReportData report = report();
        byte[] excel = new TeacherCohortAttendanceExcelExporter().export(report);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            assertEquals(1, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("August");
            assertEquals(
                    "Technological University (Hmawbi)\nDepartment of Mechatronics",
                    sheet.getRow(0).getCell(0).getStringCellValue()
            );
            assertEquals("(2026-2027) Academic Year", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Class - V MC (First Sem)", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("August 2026", sheet.getRow(2).getCell(3).getStringCellValue());
            assertEquals("IM-501 Industrial Management", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("RA-503 Robotic Analysis", sheet.getRow(3).getCell(4).getStringCellValue());
            assertEquals("Total", sheet.getRow(3).getCell(5).getStringCellValue());
            assertEquals("Remark", sheet.getRow(3).getCell(6).getStringCellValue());
            assertEquals(90, sheet.getRow(3).getCell(3).getCellStyle().getRotation());

            assertEquals("V MC-1", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals(75d, sheet.getRow(4).getCell(3).getNumericCellValue());
            assertEquals(100d, sheet.getRow(4).getCell(4).getNumericCellValue());
            assertEquals(
                    "IF(COUNT(D5:E5)=0,0,ROUND(AVERAGE(D5:E5),2))",
                    sheet.getRow(4).getCell(5).getCellFormula()
            );
            assertEquals(87.5d, sheet.getRow(4).getCell(5).getNumericCellValue());
            assertEquals("0.##", sheet.getRow(4).getCell(5).getCellStyle().getDataFormatString());
            assertEquals(CellType.BLANK, sheet.getRow(4).getCell(6).getCellType());

            assertEquals(CellType.BLANK, sheet.getRow(5).getCell(4).getCellType());
            assertEquals(50d, sheet.getRow(5).getCell(5).getNumericCellValue());
            assertEquals(CellType.BLANK, sheet.getRow(6).getCell(3).getCellType());
            assertEquals(CellType.BLANK, sheet.getRow(6).getCell(4).getCellType());
            assertEquals(0d, sheet.getRow(6).getCell(5).getNumericCellValue());

            List<String> merged = sheet.getMergedRegions().stream()
                    .map(region -> region.formatAsString())
                    .toList();
            assertTrue(merged.contains("A1:G1"));
            assertTrue(merged.contains("A2:G2"));
            assertTrue(merged.contains("A3:C3"));
            assertTrue(merged.contains("D3:G3"));
            assertTrue(merged.contains("A9:C9"));
            assertTrue(merged.contains("E9:G9"));

            assertEquals(PrintSetup.A4_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
            assertFalse(sheet.getPrintSetup().getLandscape());
            assertEquals(90, sheet.getPrintSetup().getScale());
            assertEquals(0, sheet.getPrintSetup().getFitWidth());
            assertEquals(0, sheet.getPrintSetup().getFitHeight());
            assertFalse(sheet.getFitToPage());
            assertEquals("Form No. TUHMB-029", sheet.getHeader().getRight());
            assertEquals("TUHMB/F-029/Rev-0/25-2-2022", sheet.getFooter().getLeft());
            assertEquals((int) (5.285 * 256), sheet.getColumnWidth(0));
            assertEquals((int) (12.285 * 256), sheet.getColumnWidth(1));
            assertEquals(24 * 256, sheet.getColumnWidth(2));
            assertEquals((int) (5.855 * 256), sheet.getColumnWidth(3));
            assertEquals((int) (7.14 * 256), sheet.getColumnWidth(5));
            assertEquals((int) (6.71 * 256), sheet.getColumnWidth(6));
            assertEquals(
                    12,
                    workbook.getFontAt(sheet.getRow(4).getCell(1).getCellStyle().getFontIndex())
                            .getFontHeightInPoints()
            );
            assertNull(sheet.getPaneInformation());
            assertFalse(sheet.isDisplayGridlines());
            assertTrue(sheet.getRow(8).getCell(0).getStringCellValue().contains("Prepared by;"));
            assertTrue(sheet.getRow(8).getCell(4).getStringCellValue().contains("Approved by;"));
        }

        Path directory = Path.of("target", "report-verification");
        Files.createDirectories(directory);
        Files.write(directory.resolve("overall-attendance-template.xlsx"), excel);
    }

    @Test
    void createsReferencePrintLayoutForSevenCourseRoster() throws Exception {
        TeacherCohortAttendanceReportData report = representativeReport();
        byte[] excel = new TeacherCohortAttendanceExcelExporter().export(report);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheet("August");
            assertEquals("MC-501 Course 1", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("MC-507 Course 7", sheet.getRow(3).getCell(9).getStringCellValue());
            assertEquals("Total", sheet.getRow(3).getCell(10).getStringCellValue());
            assertEquals("Remark", sheet.getRow(3).getCell(11).getStringCellValue());
            assertEquals("V MC-29", sheet.getRow(32).getCell(1).getStringCellValue());
            assertEquals(
                    "IF(COUNT(D5:J5)=0,0,ROUND(AVERAGE(D5:J5),2))",
                    sheet.getRow(4).getCell(10).getCellFormula()
            );
            assertTrue(workbook.getPrintArea(0).endsWith("$A$1:$L$35"));
            assertEquals(90, sheet.getPrintSetup().getScale());
            assertEquals("Form No. TUHMB-029", sheet.getHeader().getRight());
            assertEquals("TUHMB/F-029/Rev-0/25-2-2022", sheet.getFooter().getLeft());
        }

        Path directory = Path.of("target", "report-verification");
        Files.createDirectories(directory);
        Files.write(directory.resolve("overall-attendance-seven-course.xlsx"), excel);
    }

    private TeacherCohortAttendanceReportData report() {
        UUID firstCourse = UUID.randomUUID();
        UUID secondCourse = UUID.randomUUID();
        List<TeacherCohortAttendanceReportData.CourseColumn> courses = List.of(
                new TeacherCohortAttendanceReportData.CourseColumn(
                        firstCourse,
                        "IM-501",
                        "Industrial Management",
                        "FIRST",
                        "2026-2027"
                ),
                new TeacherCohortAttendanceReportData.CourseColumn(
                        secondCourse,
                        "RA-503",
                        "Robotic Analysis",
                        "FIRST",
                        "2026-2027"
                )
        );
        List<TeacherCohortAttendanceReportData.StudentRow> students = List.of(
                new TeacherCohortAttendanceReportData.StudentRow(
                        UUID.randomUUID(),
                        "V MC-1",
                        "First Student",
                        Map.of(
                                firstCourse, new BigDecimal("75.00"),
                                secondCourse, new BigDecimal("100.00")
                        ),
                        new BigDecimal("87.50")
                ),
                new TeacherCohortAttendanceReportData.StudentRow(
                        UUID.randomUUID(),
                        "VMC-2",
                        "Second Student",
                        Map.of(firstCourse, new BigDecimal("50.00")),
                        new BigDecimal("50.00")
                ),
                new TeacherCohortAttendanceReportData.StudentRow(
                        UUID.randomUUID(),
                        "VMC-3",
                        "No Course Student",
                        Map.of(),
                        new BigDecimal("0.00")
                )
        );
        return report(courses, students);
    }

    private TeacherCohortAttendanceReportData representativeReport() {
        List<TeacherCohortAttendanceReportData.CourseColumn> courses = IntStream.rangeClosed(1, 7)
                .mapToObj(number -> new TeacherCohortAttendanceReportData.CourseColumn(
                        UUID.randomUUID(),
                        "MC-50" + number,
                        "Course " + number,
                        "FIRST",
                        "2026-2027"
                ))
                .toList();
        List<TeacherCohortAttendanceReportData.StudentRow> students = IntStream.rangeClosed(1, 29)
                .mapToObj(number -> {
                    Map<UUID, BigDecimal> percentages = new LinkedHashMap<>();
                    courses.forEach(course -> percentages.put(course.courseId(), new BigDecimal("100.00")));
                    return new TeacherCohortAttendanceReportData.StudentRow(
                            UUID.randomUUID(),
                            "V MC-" + number,
                            "Student " + number,
                            percentages,
                            new BigDecimal("100.00")
                    );
                })
                .toList();
        return report(courses, students);
    }

    private TeacherCohortAttendanceReportData report(
            List<TeacherCohortAttendanceReportData.CourseColumn> courses,
            List<TeacherCohortAttendanceReportData.StudentRow> students
    ) {
        return new TeacherCohortAttendanceReportData(
                UUID.randomUUID(),
                "Dashboard Teacher",
                UUID.randomUUID(),
                "MC",
                "Mechatronics",
                5,
                StudentAttendancePeriod.MONTH,
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-26T08:00:00Z"),
                "ARITHMETIC_MEAN_OF_COURSE_PERCENTAGES",
                courses,
                students
        );
    }
}
