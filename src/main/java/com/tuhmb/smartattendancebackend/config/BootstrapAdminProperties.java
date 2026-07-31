package com.tuhmb.smartattendancebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(
        boolean enabled,
        String email,
        String password,
        String firstName,
        String lastName
) {
}
