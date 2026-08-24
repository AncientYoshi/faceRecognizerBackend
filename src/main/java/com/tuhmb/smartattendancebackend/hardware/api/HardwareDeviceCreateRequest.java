package com.tuhmb.smartattendancebackend.hardware.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HardwareDeviceCreateRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, numbers, dot, underscore, or hyphen")
        String deviceId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(min = 16, max = 200) String deviceKey,
        @Size(max = 100) String room,
        UUID courseId,
        Boolean enabled
) {
}
