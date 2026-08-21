package com.brainserve.onboarding.organization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "client_onboarding")
public class Permission {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String code;
    @Column(nullable = false, length = 60) private String category;
    @Column(nullable = false, length = 240) private String description;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Permission() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
}
