package com.tuhmb.smartattendancebackend.settings.api;

import com.tuhmb.smartattendancebackend.settings.domain.SystemSetting;

import java.time.Instant;
import java.util.UUID;

public record SystemSettingResponse(
        UUID id,
        String key,
        String value,
        String valueType,
        String description,
        UUID updatedBy,
        Instant updatedAt
) {
    public static SystemSettingResponse from(SystemSetting setting) {
        return new SystemSettingResponse(
                setting.getId(),
                setting.getKey(),
                setting.getValue(),
                setting.getValueType().name(),
                setting.getDescription(),
                setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getId(),
                setting.getUpdatedAt()
        );
    }
}
