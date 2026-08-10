package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class StudentAttendancePercentageExcelExporter {

    public byte[] export(StudentAttendancePercentageReportData report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Student Attendance");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 5);
            Styles styles = styles(workbook);

            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 8));
            Cell title = sheet.createRow(0).createCell(0);
            title.setCellValue("Smart Attendance - Student Percentage Report");
            title.setCellStyle(styles.title());

            Row period = sheet.createRow(2);
            period.createCell(0).setCellValue("Period");
            period.createCell(1).setCellValue(report.period().name());
            period.createCell(3).setCellValue("Date range");
            period.createCell(4).setCellValue(report.from() + " to " + report.to());
            Row filters = sheet.createRow(3);
            filters.createCell(0).setCellValue("Course ID");
            filters.createCell(1).setCellValue(report.courseId() == null ? "All assigned courses" : report.courseId().toString());
            filters.createCell(3).setCellValue("Search");
            filters.createCell(4).setCellValue(report.query().isBlank() ? "All students" : report.query());

            String[] headers = {
                    "Student Number", "Student Name", "Course Code", "Course Name",
                    "Total Sessions", "Present", "Absent", "Attendance %", "Student ID"
            };
            Row header = sheet.createRow(4);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(styles.header());
            }

            int rowIndex = 5;
            for (StudentAttendancePercentageResponse student : report.students()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(student.studentNumber());
                row.createCell(1).setCellValue(student.studentName());
                row.createCell(2).setCellValue(student.courseCode());
                row.createCell(3).setCellValue(student.courseName());
                row.createCell(4).setCellValue(student.totalSessions());
                row.createCell(5).setCellValue(student.presentSessions());
                row.createCell(6).setCellValue(student.absentSessions());
                Cell percentage = row.createCell(7);
                percentage.setCellValue(student.attendancePercentage().doubleValue() / 100d);
                percentage.setCellStyle(styles.percentage());
                row.createCell(8).setCellValue(student.studentId().toString());
            }
            if (rowIndex > 5) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(4, rowIndex - 1, 0, 8));
            }
            int[] widths = {20, 28, 18, 32, 16, 12, 12, 18, 38};
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, widths[index] * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate student attendance Excel report", exception);
        }
    }

    private Styles styles(Workbook workbook) {
        Font whiteBold = workbook.createFont();
        whiteBold.setBold(true);
        whiteBold.setColor(IndexedColors.WHITE.getIndex());
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleFont.setFontHeightInPoints((short) 16);

        CellStyle title = workbook.createCellStyle();
        title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setFont(titleFont);
        CellStyle header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(whiteBold);
        header.setBorderBottom(BorderStyle.MEDIUM);
        CellStyle percentage = workbook.createCellStyle();
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        return new Styles(title, header, percentage);
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle percentage) {
    }
}
