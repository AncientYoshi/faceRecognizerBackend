package com.tuhmb.smartattendancebackend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken) {
}
