package com.tuhmb.smartattendancebackend.attendance.api;

import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID studentId,
        UUID studentUserId,
        String studentNumber,
        String studentName,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID sessionId,
        Instant attendanceTime,
        String status,
        BigDecimal similarityScore,
        Instant verifiedAt
) {
    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getStudent().getId(),
                attendance.getStudent().getUser().getId(),
                attendance.getStudent().getStudentNumber(),
                attendance.getStudent().getUser().getFirstName()
                        + " "
                        + attendance.getStudent().getUser().getLastName(),
                attendance.getCourse().getId(),
                attendance.getCourse().getCode(),
                attendance.getCourse().getName(),
                attendance.getSession().getId(),
                attendance.getAttendanceTime(),
                attendance.getStatus().name(),
                attendance.getSimilarityScore(),
                attendance.getVerifiedAt()
        );
    }
}
