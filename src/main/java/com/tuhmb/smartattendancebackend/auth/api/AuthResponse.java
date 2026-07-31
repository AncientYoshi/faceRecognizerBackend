package com.tuhmb.smartattendancebackend.auth.api;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
