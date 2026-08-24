package com.tuhmb.smartattendancebackend.hardware.api;

import com.tuhmb.smartattendancebackend.hardware.service.HardwareAttendanceService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/hardware/v1")
public class HardwareAttendanceController {

    private final HardwareAttendanceService hardwareAttendanceService;

    public HardwareAttendanceController(HardwareAttendanceService hardwareAttendanceService) {
        this.hardwareAttendanceService = hardwareAttendanceService;
    }

    @PostMapping(value = "/attendance/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HardwareAttendanceResponse identify(
            @Parameter(description = "Configured ESP32 device identifier", required = true)
            @RequestHeader("X-Device-Id") String deviceId,
            @Parameter(description = "Configured ESP32 device secret", required = true)
            @RequestHeader("X-Device-Key") String deviceKey,
            @Parameter(description = "Optional explicit session UUID; omit it for device-bound automatic discovery")
            @RequestParam(required = false) UUID sessionId,
            @RequestPart("image") MultipartFile image
    ) {
        return hardwareAttendanceService.identify(deviceId, deviceKey, sessionId, image);
    }

    @org.springframework.web.bind.annotation.GetMapping("/attendance/active-session")
    public HardwareActiveSessionResponse activeSession(
            @Parameter(description = "Configured ESP32 device identifier", required = true)
            @RequestHeader("X-Device-Id") String deviceId,
            @Parameter(description = "Configured ESP32 device secret", required = true)
            @RequestHeader("X-Device-Key") String deviceKey
    ) {
        return hardwareAttendanceService.activeSession(deviceId, deviceKey);
    }
}
