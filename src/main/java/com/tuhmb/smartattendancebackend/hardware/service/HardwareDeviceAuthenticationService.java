package com.tuhmb.smartattendancebackend.hardware.service;

import com.tuhmb.smartattendancebackend.config.HardwareProperties;
import com.tuhmb.smartattendancebackend.hardware.domain.HardwareDevice;
import com.tuhmb.smartattendancebackend.hardware.exception.HardwareAuthenticationException;
import com.tuhmb.smartattendancebackend.hardware.repository.HardwareDeviceRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class HardwareDeviceAuthenticationService {

    private final HardwareProperties properties;
    private final HardwareDeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;

    public HardwareDeviceAuthenticationService(
            HardwareProperties properties,
            HardwareDeviceRepository deviceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AuthenticatedHardwareDevice authenticate(String deviceId, String deviceKey) {
        HardwareDevice managedDevice = deviceRepository
                .findByDeviceIdIgnoreCase(deviceId == null ? "" : deviceId.trim())
                .orElse(null);
        if (managedDevice != null) {
            if (!managedDevice.isEnabled()
                    || deviceKey == null
                    || !passwordEncoder.matches(deviceKey, managedDevice.getKeyHash())) {
                throw new HardwareAuthenticationException("Invalid hardware device credentials");
            }
            return new AuthenticatedHardwareDevice(
                    managedDevice.getDeviceId(),
                    managedDevice.getRoom(),
                    managedDevice.getCourse() == null ? null : managedDevice.getCourse().getId(),
                    true
            );
        }

        boolean idMatches = secureEquals(properties.deviceId(), deviceId);
        boolean keyMatches = secureEquals(properties.deviceKey(), deviceKey);
        if (!(idMatches & keyMatches)) {
            throw new HardwareAuthenticationException("Invalid hardware device credentials");
        }
        return new AuthenticatedHardwareDevice(deviceId, null, null, false);
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
