package com.tuhmb.smartattendancebackend.hardware.service;

import java.util.UUID;

record AuthenticatedHardwareDevice(
        String deviceId,
        String room,
        UUID courseId,
        boolean databaseManaged
) {
    boolean hasBinding() {
        return room != null || courseId != null;
    }
}
