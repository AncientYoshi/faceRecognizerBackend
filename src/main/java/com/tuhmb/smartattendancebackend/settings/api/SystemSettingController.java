package com.tuhmb.smartattendancebackend.settings.api;

import com.tuhmb.smartattendancebackend.settings.service.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system-settings")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingController {

    private final SystemSettingService settingService;

    public SystemSettingController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public List<SystemSettingResponse> findAll() {
        return settingService.findAll();
    }

    @GetMapping("/{key}")
    public SystemSettingResponse get(@PathVariable String key) {
        return settingService.get(key);
    }

    @PutMapping("/{key}")
    public SystemSettingResponse update(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemSettingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return settingService.update(key, request.value(), jwt);
    }
}
