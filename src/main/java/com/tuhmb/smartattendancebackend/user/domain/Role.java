package com.tuhmb.smartattendancebackend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
