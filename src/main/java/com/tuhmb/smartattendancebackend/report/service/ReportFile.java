package com.tuhmb.smartattendancebackend.report.service;

public record ReportFile(
        byte[] bytes,
        String filename,
        String contentType
) {
}
