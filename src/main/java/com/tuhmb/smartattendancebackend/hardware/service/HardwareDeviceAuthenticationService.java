package com.tuhmb.smartattendancebackend.hardware.service;

import com.tuhmb.smartattendancebackend.config.HardwareProperties;
import com.tuhmb.smartattendancebackend.hardware.exception.HardwareAuthenticationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class HardwareDeviceAuthenticationService {

    private final HardwareProperties properties;

    public HardwareDeviceAuthenticationService(HardwareProperties properties) {
        this.properties = properties;
    }

    public void authenticate(String deviceId, String deviceKey) {
        boolean idMatches = secureEquals(properties.deviceId(), deviceId);
        boolean keyMatches = secureEquals(properties.deviceKey(), deviceKey);
        if (!(idMatches & keyMatches)) {
            throw new HardwareAuthenticationException("Invalid hardware device credentials");
        }
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
