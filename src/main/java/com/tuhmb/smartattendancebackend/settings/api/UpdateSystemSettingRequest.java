package com.tuhmb.smartattendancebackend.settings.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingRequest(
        @NotBlank @Size(max = 500) String value
) {
}
