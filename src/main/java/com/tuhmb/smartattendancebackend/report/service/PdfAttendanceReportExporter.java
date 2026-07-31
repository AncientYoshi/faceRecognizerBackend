package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class PdfAttendanceReportExporter {

    private static final PDRectangle PAGE_SIZE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 30;
    private static final float ROW_HEIGHT = 17;
    private static final float[] WIDTHS = {70, 120, 65, 125, 65, 55, 65, 105};
    private static final String[] HEADERS = {
            "Student No.", "Student", "Course", "Course Name", "Date", "Status", "Score", "Verified At"
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ZoneId zoneId;

    public PdfAttendanceReportExporter(
            @org.springframework.beans.factory.annotation.Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.zoneId = ZoneId.of(timeZone);
    }

    public byte[] export(AttendanceReportData report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PageState state = newPage(document, regular, bold, report, 1, true);
            int pageNumber = 1;

            for (AttendanceResponse record : report.records()) {
                if (state.y() - ROW_HEIGHT < MARGIN + 18) {
                    state.stream().close();
                    pageNumber++;
                    state = newPage(document, regular, bold, report, pageNumber, false);
                }
                drawRow(state.stream(), regular, state.y(), record);
                state = new PageState(state.stream(), state.y() - ROW_HEIGHT);
            }
            state.stream().close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate attendance PDF report", exception);
        }
    }

    private PageState newPage(
            PDDocument document,
            PDType1Font regular,
            PDType1Font bold,
            AttendanceReportData report,
            int pageNumber,
            boolean includeSummary
    ) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float y = PAGE_SIZE.getHeight() - MARGIN;
        text(stream, bold, 18, MARGIN, y, "Smart Attendance - Attendance Report");
        text(stream, regular, 8, PAGE_SIZE.getWidth() - 80, MARGIN - 8, "Page " + pageNumber);
        y -= 25;

        if (includeSummary) {
            text(stream, regular, 10, MARGIN, y,
                    "Generated: " + DATE_TIME.format(report.generatedAt().atZone(zoneId)));
            y -= 15;
            text(stream, regular, 10, MARGIN, y,
                    "Recorded: " + report.totalRecords()
                            + "    Expected: " + report.expectedAttendance()
                            + "    Attendance Rate: "
                            + report.attendanceRate().multiply(java.math.BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP) + "%");
            y -= 15;
            text(stream, regular, 9, MARGIN, y, primaryFilterLabel(report.filter()));
            y -= 13;
            text(stream, regular, 9, MARGIN, y, dateFilterLabel(report.filter()));
            y -= 22;
        } else {
            text(stream, regular, 9, MARGIN, y, "Attendance records - continued");
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
        for (int i = 0; i < HEADERS.length; i++) {
            text(stream, font, 8, x + 3, y - 9, HEADERS[i]);
            x += WIDTHS[i];
        }
        stream.setNonStrokingColor(Color.BLACK);
    }

    private void drawRow(
            PDPageContentStream stream,
            PDType1Font font,
            float y,
            AttendanceResponse record
    ) throws IOException {
        List<String> values = List.of(
                record.studentNumber(),
                record.studentName(),
                record.courseCode(),
                record.courseName(),
                DATE.format(record.attendanceTime().atZone(zoneId)),
                record.status(),
                record.similarityScore().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                DATE_TIME.format(record.verifiedAt().atZone(zoneId))
        );
        stream.setStrokingColor(new Color(210, 218, 226));
        stream.moveTo(MARGIN, y - ROW_HEIGHT + 3);
        stream.lineTo(MARGIN + totalWidth(), y - ROW_HEIGHT + 3);
        stream.stroke();
        float x = MARGIN;
        for (int i = 0; i < values.size(); i++) {
            text(stream, font, 7.5f, x + 3, y - 9, fit(values.get(i), WIDTHS[i] - 6));
            x += WIDTHS[i];
        }
    }

    private String primaryFilterLabel(AttendanceReportFilter filter) {
        return "Filters: course=" + value(filter.courseId())
                + ", student=" + value(filter.studentId())
                + ", department=" + value(filter.departmentId());
    }

    private String dateFilterLabel(AttendanceReportFilter filter) {
        return "Date range: from=" + value(filter.from())
                + ", to=" + value(filter.to());
    }

    private String value(Object value) {
        return value == null ? "all" : value.toString();
    }

    private String fit(String value, float width) {
        String clean = ascii(value);
        int maxCharacters = Math.max(3, (int) (width / 4.2f));
        if (clean.length() <= maxCharacters) {
            return clean;
        }
        return clean.substring(0, maxCharacters - 3) + "...";
    }

    private String ascii(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x20-\\x7E]", "?");
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
