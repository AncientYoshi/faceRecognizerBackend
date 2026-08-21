package com.tuhmb.smartattendancebackend.academic.api;

import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID studentId,
        UUID studentUserId,
        String studentNumber,
        Integer studyYear,
        String studentName,
        Instant enrolledAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getCode(),
                enrollment.getCourse().getName(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getUser().getId(),
                enrollment.getStudent().getStudentNumber(),
                enrollment.getStudent().getStudyYear(),
                enrollment.getStudent().getUser().getFirstName()
                        + " "
                        + enrollment.getStudent().getUser().getLastName(),
                enrollment.getCreatedAt()
        );
    }
}
