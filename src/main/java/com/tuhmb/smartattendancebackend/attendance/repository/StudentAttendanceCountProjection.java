package com.tuhmb.smartattendancebackend.attendance.repository;

import java.util.UUID;

public interface StudentAttendanceCountProjection {

    UUID getStudentId();

    UUID getCourseId();

    long getPresentSessions();
}
