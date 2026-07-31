package com.tuhmb.smartattendancebackend.settings.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.settings.api.SystemSettingResponse;
import com.tuhmb.smartattendancebackend.settings.domain.SettingValueType;
import com.tuhmb.smartattendancebackend.settings.domain.SystemSetting;
import com.tuhmb.smartattendancebackend.settings.repository.SystemSettingRepository;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SystemSettingService(
            SystemSettingRepository settingRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> findAll() {
        return settingRepository.findAll(Sort.by("key")).stream()
                .map(SystemSettingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SystemSettingResponse get(String key) {
        return SystemSettingResponse.from(findSetting(key));
    }

    @Transactional
    public SystemSettingResponse update(String key, String rawValue, Jwt jwt) {
        SystemSetting setting = findSetting(key);
        String value = rawValue.trim();
        validate(setting.getValueType(), value);
        UUID userId = parseSubject(jwt);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
        setting.updateValue(value, user);
        SystemSettingResponse response = SystemSettingResponse.from(setting);
        auditService.recordForUser(
                userId,
                AuditAction.SYSTEM_SETTING_UPDATED,
                "SystemSetting",
                setting.getId(),
                setting.getKey() + " updated"
        );
        return response;
    }

    private SystemSetting findSetting(String key) {
        return settingRepository.findByKeyIgnoreCase(key.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("System setting was not found"));
    }

    private void validate(SettingValueType type, String value) {
        try {
            switch (type) {
                case DECIMAL -> new BigDecimal(value);
                case INTEGER -> Integer.parseInt(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                }
                case STRING -> {
                    if (value.isBlank()) {
                        throw new IllegalArgumentException();
                    }
                }
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Setting value must be a valid " + type.name().toLowerCase());
        }
        if ((type == SettingValueType.DECIMAL && new BigDecimal(value).signum() < 0)
                || ((type == SettingValueType.DECIMAL)
                && (valueForThreshold(type, value).compareTo(BigDecimal.ONE) > 0))) {
            throw new IllegalArgumentException("Decimal thresholds must be between 0 and 1");
        }
    }

    private BigDecimal valueForThreshold(SettingValueType type, String value) {
        return type == SettingValueType.DECIMAL ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated user was not found");
        }
    }
}
