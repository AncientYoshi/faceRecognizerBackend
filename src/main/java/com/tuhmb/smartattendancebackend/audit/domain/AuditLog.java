package com.tuhmb.smartattendancebackend.audit.domain;

import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(length = 1000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    protected AuditLog() {
    }

    public AuditLog(
            AppUser user,
            AuditAction action,
            String entityType,
            String entityId,
            String details,
            String ipAddress
    ) {
        this.user = user;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public AppUser getUser() {
        return user;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
