package com.tuhmb.smartattendancebackend.report.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class TeacherCohortAttendanceExcelExporter {

    private static final String UNIVERSITY_NAME = "Technological University (Hmawbi)";
    private static final String FORM_NUMBER = "Form No. TUHMB-029";
    private static final String FORM_REVISION = "TUHMB/F-029/Rev-0/25-2-2022";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] export(TeacherCohortAttendanceReportData report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            createSheet(workbook, report, styles(workbook));
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate overall student attendance Excel report", exception);
        }
    }

    private void createSheet(
            Workbook workbook,
            TeacherCohortAttendanceReportData report,
            Styles styles
    ) {
        List<TeacherCohortAttendanceReportData.CourseColumn> courses = report.courses();
        int totalColumn = 3 + courses.size();
        int remarkColumn = totalColumn + 1;
        Sheet sheet = workbook.createSheet(sheetName(report));
        configureSheet(sheet);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(48);
        merge(sheet, 0, 0, remarkColumn);
        cell(titleRow, 0, UNIVERSITY_NAME + "\n" + departmentLabel(report.departmentName()), styles.title());

        Row academicYearRow = sheet.createRow(1);
        academicYearRow.setHeightInPoints(24);
        merge(sheet, 1, 0, remarkColumn);
        cell(academicYearRow, 0, academicYearLabel(courses), styles.academicYear());

        Row cohortRow = sheet.createRow(2);
        cohortRow.setHeightInPoints(24);
        merge(sheet, 2, 0, 2);
        merge(sheet, 2, 3, remarkColumn);
        cell(cohortRow, 0, cohortLabel(report, courses), styles.cohortLeft());
        cell(cohortRow, 3, periodLabel(report), styles.cohortRight());

        Row header = sheet.createRow(3);
        header.setHeightInPoints(215);
        cell(header, 0, "No.", styles.header());
        cell(header, 1, "Roll No.", styles.header());
        cell(header, 2, "Name", styles.header());
        for (int index = 0; index < courses.size(); index++) {
            TeacherCohortAttendanceReportData.CourseColumn course = courses.get(index);
            cell(
                    header,
                    3 + index,
                    course.courseCode() + " " + course.courseName(),
                    styles.rotatedHeader()
            );
        }
        cell(header, totalColumn, "Total", styles.rotatedHeader());
        cell(header, remarkColumn, "Remark", styles.rotatedHeader());

        int rowIndex = 4;
        int sequence = 1;
        for (TeacherCohortAttendanceReportData.StudentRow student : report.students()) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(27);
            numericCell(row, 0, sequence++, styles.center());
            cell(row, 1, student.studentNumber(), styles.body());
            cell(row, 2, student.studentName(), styles.body());
            for (int courseIndex = 0; courseIndex < courses.size(); courseIndex++) {
                Cell percentage = row.createCell(3 + courseIndex);
                percentage.setCellStyle(styles.percentage());
                BigDecimal value = student.coursePercentages().get(courses.get(courseIndex).courseId());
                if (value != null) {
                    percentage.setCellValue(value.doubleValue());
                }
            }
            Cell total = row.createCell(totalColumn);
            total.setCellStyle(styles.percentage());
            if (courses.isEmpty()) {
                total.setCellValue(student.overallAttendancePercentage().doubleValue());
            } else {
                String first = CellReference.convertNumToColString(3) + (row.getRowNum() + 1);
                String last = CellReference.convertNumToColString(totalColumn - 1) + (row.getRowNum() + 1);
                total.setCellFormula(
                        "IF(COUNT(" + first + ":" + last + ")=0,0,ROUND(AVERAGE("
                                + first + ":" + last + "),2))"
                );
            }
            Cell remark = row.createCell(remarkColumn);
            remark.setCellStyle(styles.center());
        }

        sheet.createRow(rowIndex++).setHeightInPoints(12);
        Row signatures = sheet.createRow(rowIndex);
        signatures.setHeightInPoints(78);
        merge(sheet, rowIndex, 0, 2);
        int approvalColumn = Math.max(4, remarkColumn / 2);
        merge(sheet, rowIndex, approvalColumn, remarkColumn);
        cell(
                signatures,
                0,
                "Prepared by;\n"
                        + report.teacherName()
                        + "\nTeacher\n"
                        + departmentLabel(report.departmentName()),
                styles.signature()
        );
        cell(
                signatures,
                approvalColumn,
                "Approved by;\n\nHead of Department\n" + departmentLabel(report.departmentName()),
                styles.signature()
        );

        setColumnWidths(sheet, courses.size(), totalColumn, remarkColumn);
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, remarkColumn, 0, rowIndex);
    }

    private void configureSheet(Sheet sheet) {
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setAutobreaks(false);
        sheet.setFitToPage(false);
        sheet.setHorizontallyCenter(true);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(false);
        printSetup.setScale((short) 90);
        printSetup.setFitWidth((short) 0);
        printSetup.setFitHeight((short) 0);
        sheet.getHeader().setRight(FORM_NUMBER);
        sheet.getFooter().setLeft(FORM_REVISION);
        sheet.setMargin(Sheet.LeftMargin, 0.7);
        sheet.setMargin(Sheet.RightMargin, 0.7);
        sheet.setMargin(Sheet.TopMargin, 0.75);
        sheet.setMargin(Sheet.BottomMargin, 0.75);
    }

    private void setColumnWidths(Sheet sheet, int courseCount, int totalColumn, int remarkColumn) {
        sheet.setColumnWidth(0, (int) (5.285 * 256));
        sheet.setColumnWidth(1, (int) (12.285 * 256));
        sheet.setColumnWidth(2, 24 * 256);
        for (int index = 0; index < courseCount; index++) {
            sheet.setColumnWidth(3 + index, (int) (5.855 * 256));
        }
        sheet.setColumnWidth(totalColumn, (int) (7.14 * 256));
        sheet.setColumnWidth(remarkColumn, (int) (6.71 * 256));
    }

    private String sheetName(TeacherCohortAttendanceReportData report) {
        String requested = switch (report.period()) {
            case MONTH -> report.referenceDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            case WEEK -> "Week " + report.from();
            case ALL -> "Overall";
        };
        return WorkbookUtil.createSafeSheetName(requested);
    }

    private String academicYearLabel(List<TeacherCohortAttendanceReportData.CourseColumn> courses) {
        List<String> years = courses.stream()
                .map(TeacherCohortAttendanceReportData.CourseColumn::academicYear)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (years.isEmpty()) {
            return "Academic Year";
        }
        if (years.size() == 1) {
            return "(" + years.getFirst() + ") Academic Year";
        }
        return "Multiple Academic Years";
    }

    private String cohortLabel(
            TeacherCohortAttendanceReportData report,
            List<TeacherCohortAttendanceReportData.CourseColumn> courses
    ) {
        List<String> semesters = courses.stream()
                .map(TeacherCohortAttendanceReportData.CourseColumn::semester)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String semester = semesters.size() == 1 ? " (" + semesterLabel(semesters.getFirst()) + ")" : "";
        return "Class - " + romanYear(report.studyYear()) + " " + report.departmentCode() + semester;
    }

    private String periodLabel(TeacherCohortAttendanceReportData report) {
        return switch (report.period()) {
            case MONTH -> report.referenceDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " " + report.referenceDate().getYear();
            case WEEK -> report.from().format(DISPLAY_DATE) + " to " + report.to().format(DISPLAY_DATE);
            case ALL -> "Overall as of " + report.referenceDate().format(DISPLAY_DATE);
        };
    }

    private String departmentLabel(String departmentName) {
        if (departmentName.toLowerCase(Locale.ROOT).startsWith("department")) {
            return departmentName;
        }
        return "Department of " + departmentName;
    }

    private String romanYear(Integer studyYear) {
        if (studyYear == null) {
            return "Unassigned Year";
        }
        return switch (studyYear) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "Year " + studyYear;
        };
    }

    private String semesterLabel(String semester) {
        return switch (semester.trim().toUpperCase(Locale.ROOT)) {
            case "FIRST", "FIRST_SEMESTER", "FIRST SEMESTER" -> "First Sem";
            case "SECOND", "SECOND_SEMESTER", "SECOND SEMESTER" -> "Second Sem";
            default -> semester;
        };
    }

    private void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void numericCell(Row row, int column, long value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void merge(Sheet sheet, int row, int firstColumn, int lastColumn) {
        if (firstColumn < lastColumn) {
            sheet.addMergedRegion(new CellRangeAddress(row, row, firstColumn, lastColumn));
        }
    }

    private Styles styles(Workbook workbook) {
        Font titleFont = font(workbook, 14, true);
        Font headingFont = font(workbook, 12, true);
        Font bodyFont = font(workbook, 12, false);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setWrapText(true);

        CellStyle academicYear = workbook.createCellStyle();
        academicYear.setFont(titleFont);
        academicYear.setAlignment(HorizontalAlignment.CENTER);
        academicYear.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle cohortLeft = workbook.createCellStyle();
        cohortLeft.setFont(headingFont);
        cohortLeft.setAlignment(HorizontalAlignment.LEFT);
        cohortLeft.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle cohortRight = workbook.createCellStyle();
        cohortRight.setFont(headingFont);
        cohortRight.setAlignment(HorizontalAlignment.RIGHT);
        cohortRight.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle header = bordered(workbook, bodyFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        CellStyle rotatedHeader = bordered(workbook, bodyFont);
        rotatedHeader.setAlignment(HorizontalAlignment.LEFT);
        rotatedHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        rotatedHeader.setRotation((short) 90);

        CellStyle body = bordered(workbook, bodyFont);
        body.setAlignment(HorizontalAlignment.LEFT);
        body.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle center = bordered(workbook, bodyFont);
        center.setAlignment(HorizontalAlignment.CENTER);
        center.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle percentage = bordered(workbook, bodyFont);
        percentage.setAlignment(HorizontalAlignment.CENTER);
        percentage.setVerticalAlignment(VerticalAlignment.CENTER);
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.##"));

        CellStyle signature = workbook.createCellStyle();
        signature.setFont(bodyFont);
        signature.setAlignment(HorizontalAlignment.LEFT);
        signature.setVerticalAlignment(VerticalAlignment.TOP);
        signature.setWrapText(true);

        return new Styles(
                title,
                academicYear,
                cohortLeft,
                cohortRight,
                header,
                rotatedHeader,
                body,
                center,
                percentage,
                signature
        );
    }

    private Font font(Workbook workbook, int points, boolean bold) {
        Font font = workbook.createFont();
        font.setFontName("Pyidaungsu");
        font.setFontHeightInPoints((short) points);
        font.setBold(bold);
        return font;
    }

    private CellStyle bordered(Workbook workbook, Font font) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private record Styles(
            CellStyle title,
            CellStyle academicYear,
            CellStyle cohortLeft,
            CellStyle cohortRight,
            CellStyle header,
            CellStyle rotatedHeader,
            CellStyle body,
            CellStyle center,
            CellStyle percentage,
            CellStyle signature
    ) {
    }
}
