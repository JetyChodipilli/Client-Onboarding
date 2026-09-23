package com.brainserve.clientonboarding.audit.application;

import com.brainserve.clientonboarding.audit.domain.model.AuditEntry;
import com.brainserve.clientonboarding.audit.domain.repository.AuditRepository;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('AUDIT_READ')")
    public com.brainserve.clientonboarding.common.domain.model.PageSlice<AuditEntry> page(
            UUID organizationId, int page, int size) {
        return new com.brainserve.clientonboarding.common.domain.model.PageSlice<>(
                repository.findPage(organizationId, size, (long) page * size), page, size,
                repository.count(organizationId));
    }

    public void append(UUID organizationId, UUID actorUserId, String action, String entityType,
                       UUID entityId, Map<String, ?> before, Map<String, ?> after,
                       String source, String ipHash) {
        repository.append(new AuditEntry(UUID.randomUUID(), organizationId, actorUserId, action,
                entityType, entityId, source, json(before), json(after), currentUuid(RequestIds.REQUEST_ID_MDC_KEY),
                currentUuid(RequestIds.CORRELATION_ID_MDC_KEY), ipHash, clock.instant()));
    }

    private String json(Map<String, ?> value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit metadata serialization failed", exception);
        }
    }

    private UUID currentUuid(String key) {
        String value = MDC.get(key);
        return value == null ? null : UUID.fromString(value);
    }

}
