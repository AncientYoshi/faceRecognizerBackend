package com.tuhmb.smartattendancebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai")
public record AiServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
