package com.tuhmb.smartattendancebackend.audit.repository;

import com.tuhmb.smartattendancebackend.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
