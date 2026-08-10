package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.report.api.StudentAttendancePercentageResponse;
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
import java.util.List;

@Component
public class StudentAttendancePercentagePdfExporter {

    private static final PDRectangle PAGE_SIZE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 30;
    private static final float ROW_HEIGHT = 18;
    private static final float[] WIDTHS = {90, 145, 80, 155, 60, 55, 55, 70};
    private static final String[] HEADERS = {
            "Student No.", "Student", "Course", "Course Name", "Sessions", "Present", "Absent", "Attendance"
    };

    public byte[] export(StudentAttendancePercentageReportData report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PageState state = newPage(document, regular, bold, report, 1, true);
            int pageNumber = 1;
            for (StudentAttendancePercentageResponse student : report.students()) {
                if (state.y() - ROW_HEIGHT < MARGIN + 18) {
                    state.stream().close();
                    state = newPage(document, regular, bold, report, ++pageNumber, false);
                }
                drawRow(state.stream(), regular, state.y(), student);
                state = new PageState(state.stream(), state.y() - ROW_HEIGHT);
            }
            state.stream().close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate student attendance PDF report", exception);
        }
    }

    private PageState newPage(
            PDDocument document,
            PDType1Font regular,
            PDType1Font bold,
            StudentAttendancePercentageReportData report,
            int pageNumber,
            boolean summary
    ) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float y = PAGE_SIZE.getHeight() - MARGIN;
        text(stream, bold, 18, MARGIN, y, "Smart Attendance - Student Percentage Report");
        text(stream, regular, 8, PAGE_SIZE.getWidth() - 80, MARGIN - 8, "Page " + pageNumber);
        y -= 25;
        if (summary) {
            text(stream, regular, 10, MARGIN, y,
                    "Period: " + report.period() + "    Date range: " + report.from() + " to " + report.to());
            y -= 15;
            text(stream, regular, 9, MARGIN, y,
                    "Course: " + value(report.courseId(), "all assigned courses")
                            + "    Search: " + value(report.query(), "all students"));
            y -= 22;
        } else {
            text(stream, regular, 9, MARGIN, y, "Student percentage report - continued");
            y -= 20;
        }
        drawHeader(stream, bold, y);
        return new PageState(stream, y - ROW_HEIGHT);
    }

    private void drawHeader(PDPageContentStream stream, PDType1Font font, float y) throws IOException {
        stream.setNonStrokingColor(new Color(29, 78, 121));
        stream.addRect(MARGIN, y - ROW_HEIGHT + 3, totalWidth(), ROW_HEIGHT);
        stream.fill();
        stream.setNonStrokingColor(Color.WHITE);
        float x = MARGIN;
        for (int index = 0; index < HEADERS.length; index++) {
            text(stream, font, 8, x + 3, y - 10, HEADERS[index]);
            x += WIDTHS[index];
        }
        stream.setNonStrokingColor(Color.BLACK);
    }

    private void drawRow(
            PDPageContentStream stream,
            PDType1Font font,
            float y,
            StudentAttendancePercentageResponse student
    ) throws IOException {
        List<String> values = List.of(
                student.studentNumber(),
                student.studentName(),
                student.courseCode(),
                student.courseName(),
                Long.toString(student.totalSessions()),
                Long.toString(student.presentSessions()),
                Long.toString(student.absentSessions()),
                student.attendancePercentage().toPlainString() + "%"
        );
        stream.setStrokingColor(new Color(210, 218, 226));
        stream.moveTo(MARGIN, y - ROW_HEIGHT + 3);
        stream.lineTo(MARGIN + totalWidth(), y - ROW_HEIGHT + 3);
        stream.stroke();
        float x = MARGIN;
        for (int index = 0; index < values.size(); index++) {
            text(stream, font, 7.5f, x + 3, y - 10, fit(values.get(index), WIDTHS[index] - 6));
            x += WIDTHS[index];
        }
    }

    private String value(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private String fit(String value, float width) {
        String clean = ascii(value);
        int maximum = Math.max(3, (int) (width / 4.2f));
        return clean.length() <= maximum ? clean : clean.substring(0, maximum - 3) + "...";
    }

    private String ascii(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private float totalWidth() {
        float total = 0;
        for (float width : WIDTHS) {
            total += width;
        }
        return total;
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

    private record PageState(PDPageContentStream stream, float y) {
    }
}
