package com.tuhmb.smartattendancebackend.report.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Component
public class StudentAttendancePercentageExcelExporter {

    private static final DateTimeFormatter CALL_DATE = DateTimeFormatter.ofPattern("dd/MM");

    public byte[] export(StudentAttendancePercentageReportData report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = styles(workbook);
            if (report.registers().isEmpty()) {
                Sheet sheet = workbook.createSheet("Attendance Register");
                sheet.createRow(0).createCell(0).setCellValue("No attendance data for the selected period");
            } else {
                int index = 1;
                for (CourseRollCallRegister register : report.registers()) {
                    createRegisterSheet(workbook, styles, register, report, index++);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate student attendance Excel report", exception);
        }
    }

    private void createRegisterSheet(
            Workbook workbook,
            Styles styles,
            CourseRollCallRegister register,
            StudentAttendancePercentageReportData report,
            int sheetNumber
    ) {
        String baseName = WorkbookUtil.createSafeSheetName(register.courseCode());
        String sheetName = uniqueSheetName(workbook, baseName.isBlank() ? "Course " + sheetNumber : baseName);
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(3, 5);
        sheet.setAutobreaks(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setFitToPage(true);

        int lastColumn = 3 + register.columns().size() + 2;
        merge(sheet, 0, 0, lastColumn);
        Cell title = sheet.createRow(0).createCell(0);
        title.setCellValue("Technological University (Hmawbi)");
        title.setCellStyle(styles.title());

        merge(sheet, 1, 0, lastColumn);
        Cell subtitle = sheet.createRow(1).createCell(0);
        subtitle.setCellValue("Attendance Record (" + register.academicYear() + ")");
        subtitle.setCellStyle(styles.subtitle());

        merge(sheet, 2, 0, lastColumn);
        Cell details = sheet.createRow(2).createCell(0);
        details.setCellValue(
                "Department: " + register.departmentName()
                        + "    Study Year: " + yearLabel(register.studyYear())
                        + "    Course: " + register.courseCode() + " - " + register.courseName()
        );
        details.setCellStyle(styles.subtitle());

        merge(sheet, 3, 0, lastColumn);
        Cell period = sheet.createRow(3).createCell(0);
        period.setCellValue(
                "Period: " + report.from() + " to " + report.to()
                        + "    Total roll calls: " + register.columns().size()
        );
        period.setCellStyle(styles.subtitle());

        Row header = sheet.createRow(4);
        header.setHeightInPoints(34);
        int column = 0;
        header(header, column++, "No.", styles);
        header(header, column++, "Roll No.", styles);
        header(header, column++, "Student Name", styles);
        for (CourseRollCallRegister.RollCallColumn call : register.columns()) {
            header(header, column++, CALL_DATE.format(call.sessionDate()) + "\n#" + call.callNumber(), styles);
        }
        header(header, column++, "Absent", styles);
        header(header, column++, "Present", styles);
        header(header, column, "Attendance %", styles);

        int rowIndex = 5;
        int studentNumber = 1;
        for (CourseRollCallRegister.StudentRow student : register.students()) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(22);
            int cellIndex = 0;
            body(row, cellIndex++, studentNumber++, styles.center());
            body(row, cellIndex++, student.studentNumber(), styles.body());
            body(row, cellIndex++, student.studentName(), styles.body());
            for (Boolean present : student.presence()) {
                body(row, cellIndex++, present ? "P" : "A", present ? styles.present() : styles.absent());
            }
            body(row, cellIndex++, student.absentRollCalls(), styles.center());
            body(row, cellIndex++, student.presentRollCalls(), styles.center());
            Cell percentage = row.createCell(cellIndex);
            percentage.setCellValue(student.attendancePercentage().doubleValue() / 100d);
            percentage.setCellStyle(styles.percentage());
        }

        sheet.setColumnWidth(0, 7 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        for (int callColumn = 0; callColumn < register.columns().size(); callColumn++) {
            sheet.setColumnWidth(3 + callColumn, 8 * 256);
        }
        sheet.setColumnWidth(lastColumn - 2, 11 * 256);
        sheet.setColumnWidth(lastColumn - 1, 11 * 256);
        sheet.setColumnWidth(lastColumn, 16 * 256);
        sheet.setRepeatingRows(new CellRangeAddress(0, 4, -1, -1));
        workbook.setPrintArea(
                workbook.getSheetIndex(sheet),
                0,
                lastColumn,
                0,
                Math.max(5, rowIndex - 1)
        );
    }

    private void header(Row row, int index, String value, Styles styles) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(styles.header());
    }

    private void body(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void body(Row row, int index, long value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void merge(Sheet sheet, int row, int firstColumn, int lastColumn) {
        sheet.addMergedRegion(new CellRangeAddress(row, row, firstColumn, lastColumn));
    }

    private String uniqueSheetName(Workbook workbook, String requested) {
        String base = requested.length() > 31 ? requested.substring(0, 31) : requested;
        String candidate = base;
        int suffix = 2;
        while (workbook.getSheet(candidate) != null) {
            String ending = "-" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - ending.length())) + ending;
        }
        return candidate;
    }

    private String yearLabel(Integer year) {
        return year == null ? "Unassigned" : switch (year) {
            case 1 -> "First Year";
            case 2 -> "Second Year";
            case 3 -> "Third Year";
            case 4 -> "Fourth Year";
            case 5 -> "Fifth Year";
            case 6 -> "Sixth Year";
            default -> year.toString();
        };
    }

    private Styles styles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        Font bold = workbook.createFont();
        bold.setBold(true);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        CellStyle subtitle = workbook.createCellStyle();
        subtitle.setFont(bold);
        subtitle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle header = bordered(workbook);
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        CellStyle body = bordered(workbook);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle center = bordered(workbook);
        center.setAlignment(HorizontalAlignment.CENTER);
        center.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle present = bordered(workbook);
        present.setAlignment(HorizontalAlignment.CENTER);
        present.setFont(bold);
        present.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        present.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle absent = bordered(workbook);
        absent.setAlignment(HorizontalAlignment.CENTER);
        absent.setFont(bold);
        absent.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        absent.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle percentage = bordered(workbook);
        percentage.setAlignment(HorizontalAlignment.CENTER);
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        return new Styles(title, subtitle, header, body, center, present, absent, percentage);
    }

    private CellStyle bordered(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private record Styles(
            CellStyle title,
            CellStyle subtitle,
            CellStyle header,
            CellStyle body,
            CellStyle center,
            CellStyle present,
            CellStyle absent,
            CellStyle percentage
    ) {
    }
}
