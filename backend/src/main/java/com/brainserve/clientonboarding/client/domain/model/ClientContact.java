package com.brainserve.clientonboarding.client.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ClientContact(UUID id, UUID organizationId, UUID clientId, String name, String email,
                            String phone, String jobTitle, boolean primary, Instant archivedAt,
                            Instant createdAt, Instant updatedAt, long version) { }
