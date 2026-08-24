package com.tuhmb.smartattendancebackend.hardware.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HardwareDeviceUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String room,
        UUID courseId,
        @Size(min = 16, max = 200)
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String newDeviceKey,
        @NotNull Boolean enabled
) {
}
