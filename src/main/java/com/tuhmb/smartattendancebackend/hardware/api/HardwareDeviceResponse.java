package com.tuhmb.smartattendancebackend.hardware.api;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.hardware.domain.HardwareDevice;

import java.time.Instant;
import java.util.UUID;

public record HardwareDeviceResponse(
        UUID id,
        String deviceId,
        String name,
        String room,
        UUID courseId,
        String courseCode,
        String courseName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static HardwareDeviceResponse from(HardwareDevice device) {
        Course course = device.getCourse();
        return new HardwareDeviceResponse(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.getRoom(),
                course == null ? null : course.getId(),
                course == null ? null : course.getCode(),
                course == null ? null : course.getName(),
                device.isEnabled(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
