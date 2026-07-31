package com.tuhmb.smartattendancebackend.dashboard.api;

import java.util.UUID;

public record TeacherCourseSummary(
        UUID courseId,
        String courseCode,
        String courseName,
        long enrolledStudents
) {
}
