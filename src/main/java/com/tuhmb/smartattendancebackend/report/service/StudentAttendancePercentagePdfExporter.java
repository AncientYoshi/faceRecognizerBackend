package com.tuhmb.smartattendancebackend.report.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class StudentAttendancePercentagePdfExporter {

    private static final PDRectangle PAGE_SIZE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 30;
    private static final float ROW_HEIGHT = 18;
    private static final int ROWS_PER_PAGE = 24;
    private static final int CALLS_PER_PANEL = 35;
    private static final DateTimeFormatter CALL_DATE = DateTimeFormatter.ofPattern("dd");

    public byte[] export(StudentAttendancePercentageReportData report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int pageNumber = 0;
            if (report.registers().isEmpty()) {
                PDPage page = new PDPage(PAGE_SIZE);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    centered(stream, bold, 16, PAGE_SIZE.getHeight() - 60,
                            "Technological University (Hmawbi)");
                    centered(stream, regular, 11, PAGE_SIZE.getHeight() - 85,
                            "No attendance data for the selected period");
                }
            }
            for (CourseRollCallRegister register : report.registers()) {
                int panelCount = Math.max(1, (register.columns().size() + CALLS_PER_PANEL - 1) / CALLS_PER_PANEL);
                int rowPageCount = Math.max(1, (register.students().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
                for (int panel = 0; panel < panelCount; panel++) {
                    int callFrom = panel * CALLS_PER_PANEL;
                    int callTo = Math.min(register.columns().size(), callFrom + CALLS_PER_PANEL);
                    for (int rowPage = 0; rowPage < rowPageCount; rowPage++) {
                        int rowFrom = rowPage * ROWS_PER_PAGE;
                        int rowTo = Math.min(register.students().size(), rowFrom + ROWS_PER_PAGE);
                        drawPage(
                                document, regular, bold, report, register,
                                callFrom, callTo, rowFrom, rowTo,
                                ++pageNumber, panel + 1, panelCount
                        );
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate student attendance PDF report", exception);
        }
    }

    private void drawPage(
            PDDocument document,
            PDType1Font regular,
            PDType1Font bold,
            StudentAttendancePercentageReportData report,
            CourseRollCallRegister register,
            int callFrom,
            int callTo,
            int rowFrom,
            int rowTo,
            int pageNumber,
            int panel,
            int panelCount
    ) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            float y = PAGE_SIZE.getHeight() - MARGIN;
            centered(stream, bold, 16, y, "Technological University (Hmawbi)");
            y -= 19;
            centered(stream, bold, 12, y, "Attendance Record (" + register.academicYear() + ")");
            y -= 17;
            centered(stream, regular, 9, y,
                    "Department: " + register.departmentName()
                            + "   Study Year: " + yearLabel(register.studyYear())
                            + "   Semester: " + register.semester());
            y -= 14;
            centered(stream, bold, 9, y,
                    "Course: " + register.courseCode() + " - " + register.courseName());
            y -= 14;
            centered(stream, regular, 8, y,
                    "Period: " + report.from() + " to " + report.to()
                            + "   Total roll calls: " + register.columns().size()
                            + (panelCount > 1 ? "   Column panel: " + panel + "/" + panelCount : ""));
            text(stream, regular, 7, PAGE_SIZE.getWidth() - 64, MARGIN - 8, "Page " + pageNumber);
            y -= 24;

            int callCount = callTo - callFrom;
            float[] widths = widths(callCount);
            drawHeader(stream, bold, register.columns().subList(callFrom, callTo), y, widths);
            y -= 30;
            for (int index = rowFrom; index < rowTo; index++) {
                drawStudentRow(
                        stream, regular, register.students().get(index), index + 1,
                        callFrom, callTo, y, widths
                );
                y -= ROW_HEIGHT;
            }
        }
    }

    private float[] widths(int callCount) {
        float fixed = 25 + 72 + 145 + 42 + 42 + 50;
        float available = PAGE_SIZE.getWidth() - (2 * MARGIN) - fixed;
        float callWidth = callCount == 0 ? 0 : Math.max(10, Math.min(25, available / callCount));
        float[] widths = new float[6 + callCount];
        widths[0] = 25;
        widths[1] = 72;
        widths[2] = 145;
        for (int index = 0; index < callCount; index++) {
            widths[3 + index] = callWidth;
        }
        widths[3 + callCount] = 42;
        widths[4 + callCount] = 42;
        widths[5 + callCount] = 50;
        return widths;
    }

    private void drawHeader(
            PDPageContentStream stream,
            PDType1Font font,
            List<CourseRollCallRegister.RollCallColumn> calls,
            float y,
            float[] widths
    ) throws IOException {
        List<String> values = new java.util.ArrayList<>();
        values.add("No.");
        values.add("Roll No.");
        values.add("Student Name");
        calls.forEach(call -> values.add(CALL_DATE.format(call.sessionDate()) + "/" + call.callNumber()));
        values.add("Absent");
        values.add("Present");
        values.add("Percent");
        drawCells(stream, font, 6.5f, y, 30, widths, values, true);
    }

    private void drawStudentRow(
            PDPageContentStream stream,
            PDType1Font font,
            CourseRollCallRegister.StudentRow student,
            int number,
            int callFrom,
            int callTo,
            float y,
            float[] widths
    ) throws IOException {
        List<String> values = new java.util.ArrayList<>();
        values.add(Integer.toString(number));
        values.add(student.studentNumber());
        values.add(student.studentName());
        student.presence().subList(callFrom, callTo)
                .forEach(present -> values.add(present ? "P" : "A"));
        values.add(Long.toString(student.absentRollCalls()));
        values.add(Long.toString(student.presentRollCalls()));
        values.add(student.attendancePercentage().toPlainString() + "%");
        drawCells(stream, font, 7f, y, ROW_HEIGHT, widths, values, false);
    }

    private void drawCells(
            PDPageContentStream stream,
            PDType1Font font,
            float fontSize,
            float y,
            float height,
            float[] widths,
            List<String> values,
            boolean header
    ) throws IOException {
        float x = MARGIN;
        if (header) {
            stream.setNonStrokingColor(new Color(225, 228, 232));
            stream.addRect(x, y - height, total(widths), height);
            stream.fill();
        }
        stream.setNonStrokingColor(Color.BLACK);
        stream.setStrokingColor(new Color(70, 70, 70));
        for (int index = 0; index < widths.length; index++) {
            stream.addRect(x, y - height, widths[index], height);
            stream.stroke();
            String fitted = fit(values.get(index), widths[index] - 4);
            text(stream, font, fontSize, x + 2, y - (height / 2) - 2, fitted);
            x += widths[index];
        }
    }

    private float total(float[] widths) {
        float total = 0;
        for (float width : widths) {
            total += width;
        }
        return total;
    }

    private void centered(PDPageContentStream stream, PDType1Font font, float size, float y, String value)
            throws IOException {
        String clean = ascii(value);
        float width = font.getStringWidth(clean) / 1000 * size;
        text(stream, font, size, Math.max(MARGIN, (PAGE_SIZE.getWidth() - width) / 2), y, clean);
    }

    private String fit(String value, float width) {
        String clean = ascii(value);
        int maximum = Math.max(1, (int) (width / 4.1f));
        return clean.length() <= maximum
                ? clean
                : maximum <= 3 ? clean.substring(0, maximum) : clean.substring(0, maximum - 3) + "...";
    }

    private String ascii(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
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

    private void text(
            PDPageContentStream stream,
            PDType1Font font,
            float size,
            float x,
            float y,
            String value
    ) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(ascii(value));
        stream.endText();
    }
}
