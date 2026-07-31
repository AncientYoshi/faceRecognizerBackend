package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;

@Component
public class ExcelAttendanceReportExporter {

    private final ZoneId zoneId;

    public ExcelAttendanceReportExporter(@Value("${app.time-zone:Asia/Yangon}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    public byte[] export(AttendanceReportData report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = styles(workbook);
            createSummary(workbook, report, styles);
            createRecords(workbook, report, styles);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate attendance Excel report", exception);
        }
    }

    private void createSummary(Workbook workbook, AttendanceReportData report, Styles styles) {
        Sheet sheet = workbook.createSheet("Summary");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 2);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
        Cell title = sheet.createRow(0).createCell(0);
        title.setCellValue("Smart Attendance - Attendance Report");
        title.setCellStyle(styles.title());

        summaryRow(sheet, 2, "Total attendance records", report.totalRecords(), styles);
        summaryRow(sheet, 3, "Expected attendance", report.expectedAttendance(), styles);
        Row rate = sheet.createRow(4);
        label(rate.createCell(0), "Attendance rate", styles);
        Cell rateCell = rate.createCell(1);
        rateCell.setCellFormula("IF(B4=0,0,B3/B4)");
        rateCell.setCellStyle(styles.percentage());

        Row generated = sheet.createRow(5);
        label(generated.createCell(0), "Generated at", styles);
        Cell generatedAt = generated.createCell(1);
        generatedAt.setCellValue(report.generatedAt().atZone(zoneId).toLocalDateTime());
        generatedAt.setCellStyle(styles.dateTime());

        Row filters = sheet.createRow(7);
        filters.createCell(0).setCellValue("Filters");
        filters.getCell(0).setCellStyle(styles.section());
        filterRow(sheet, 8, "Course ID", report.filter().courseId(), styles);
        filterRow(sheet, 9, "Student ID", report.filter().studentId(), styles);
        filterRow(sheet, 10, "Department ID", report.filter().departmentId(), styles);
        filterRow(sheet, 11, "From", report.filter().from(), styles);
        filterRow(sheet, 12, "To", report.filter().to(), styles);

        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 18 * 256);
    }

    private void createRecords(Workbook workbook, AttendanceReportData report, Styles styles) {
        Sheet sheet = workbook.createSheet("Attendance Records");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        String[] headers = {
                "Attendance ID", "Student Number", "Student Name", "Course Code", "Course Name",
                "Session ID", "Attendance Time", "Status", "Similarity Score", "Verified At"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header());
        }

        int rowIndex = 1;
        for (AttendanceResponse record : report.records()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(record.id().toString());
            row.createCell(1).setCellValue(record.studentNumber());
            row.createCell(2).setCellValue(record.studentName());
            row.createCell(3).setCellValue(record.courseCode());
            row.createCell(4).setCellValue(record.courseName());
            row.createCell(5).setCellValue(record.sessionId().toString());
            Cell attendanceTime = row.createCell(6);
            attendanceTime.setCellValue(record.attendanceTime().atZone(zoneId).toLocalDateTime());
            attendanceTime.setCellStyle(styles.dateTime());
            Cell status = row.createCell(7);
            status.setCellValue(record.status());
            status.setCellStyle(styles.present());
            Cell score = row.createCell(8);
            score.setCellValue(record.similarityScore().doubleValue());
            score.setCellStyle(styles.decimal());
            Cell verifiedAt = row.createCell(9);
            verifiedAt.setCellValue(record.verifiedAt().atZone(zoneId).toLocalDateTime());
            verifiedAt.setCellStyle(styles.dateTime());
        }
        if (rowIndex > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
        }
        int[] widths = {38, 18, 24, 16, 28, 38, 20, 12, 18, 20};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void summaryRow(Sheet sheet, int index, String name, long value, Styles styles) {
        Row row = sheet.createRow(index);
        label(row.createCell(0), name, styles);
        Cell cell = row.createCell(1);
        cell.setCellValue(value);
        cell.setCellStyle(styles.integer());
    }

    private void label(Cell cell, String value, Styles styles) {
        cell.setCellValue(value);
        cell.setCellStyle(styles.label());
    }

    private void filterRow(Sheet sheet, int rowIndex, String label, Object value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        if (value instanceof java.time.LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date());
        } else {
            cell.setCellValue(value == null ? "All" : value.toString());
        }
    }

    private Styles styles(Workbook workbook) {
        Font whiteBold = workbook.createFont();
        whiteBold.setBold(true);
        whiteBold.setColor(IndexedColors.WHITE.getIndex());
        whiteBold.setFontHeightInPoints((short) 11);

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleFont.setFontHeightInPoints((short) 16);

        CellStyle title = workbook.createCellStyle();
        title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.LEFT);

        CellStyle header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(whiteBold);
        header.setBorderBottom(BorderStyle.MEDIUM);

        CellStyle section = workbook.createCellStyle();
        section.cloneStyleFrom(header);

        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle label = workbook.createCellStyle();
        label.setFont(bold);

        CellStyle integer = workbook.createCellStyle();
        integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

        CellStyle percentage = workbook.createCellStyle();
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

        CellStyle decimal = workbook.createCellStyle();
        decimal.setDataFormat(workbook.createDataFormat().getFormat("0.00000"));

        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

        CellStyle present = workbook.createCellStyle();
        present.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        present.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return new Styles(title, header, section, label, integer, percentage, decimal, date, dateTime, present);
    }

    private record Styles(
            CellStyle title,
            CellStyle header,
            CellStyle section,
            CellStyle label,
            CellStyle integer,
            CellStyle percentage,
            CellStyle decimal,
            CellStyle date,
            CellStyle dateTime,
            CellStyle present
    ) {
    }
}
