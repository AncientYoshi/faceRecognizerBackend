package com.tuhmb.smartattendancebackend.settings.repository;

import com.tuhmb.smartattendancebackend.settings.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

    Optional<SystemSetting> findByKeyIgnoreCase(String key);
}
