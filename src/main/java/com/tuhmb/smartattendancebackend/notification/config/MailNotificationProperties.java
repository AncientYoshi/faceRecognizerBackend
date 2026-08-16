package com.tuhmb.smartattendancebackend.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.mail")
public record MailNotificationProperties(
        boolean enabled,
        String from,
        String frontendBaseUrl
) {
}
