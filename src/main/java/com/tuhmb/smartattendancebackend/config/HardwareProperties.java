package com.tuhmb.smartattendancebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.hardware")
public record HardwareProperties(
        String deviceId,
        String deviceKey
) {
}
