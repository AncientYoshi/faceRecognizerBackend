package com.tuhmb.smartattendancebackend.hardware.api;

import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.hardware.service.HardwareDeviceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/hardware-devices")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class HardwareDeviceController {

    private final HardwareDeviceService hardwareDeviceService;

    public HardwareDeviceController(HardwareDeviceService hardwareDeviceService) {
        this.hardwareDeviceService = hardwareDeviceService;
    }

    @GetMapping
    public PageResponse<HardwareDeviceResponse> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return hardwareDeviceService.search(query, enabled, page, size);
    }

    @GetMapping("/{id}")
    public HardwareDeviceResponse get(@PathVariable UUID id) {
        return hardwareDeviceService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HardwareDeviceResponse create(@Valid @RequestBody HardwareDeviceCreateRequest request) {
        return hardwareDeviceService.create(request);
    }

    @PutMapping("/{id}")
    public HardwareDeviceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody HardwareDeviceUpdateRequest request
    ) {
        return hardwareDeviceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        hardwareDeviceService.delete(id);
    }
}
