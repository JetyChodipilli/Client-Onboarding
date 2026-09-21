package com.brainserve.clientonboarding.audit.domain.repository;

import com.brainserve.clientonboarding.audit.domain.model.AuditEntry;
import java.util.List;
import java.util.UUID;

public interface AuditRepository {
    void append(AuditEntry entry);
    List<AuditEntry> findPage(UUID organizationId, int limit, int offset);
    long count(UUID organizationId);
}
