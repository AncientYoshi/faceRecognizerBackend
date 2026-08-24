package com.tuhmb.smartattendancebackend.hardware.repository;

import com.tuhmb.smartattendancebackend.hardware.domain.HardwareDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface HardwareDeviceRepository
        extends JpaRepository<HardwareDevice, UUID>, JpaSpecificationExecutor<HardwareDevice> {

    Optional<HardwareDevice> findByDeviceIdIgnoreCase(String deviceId);
}
