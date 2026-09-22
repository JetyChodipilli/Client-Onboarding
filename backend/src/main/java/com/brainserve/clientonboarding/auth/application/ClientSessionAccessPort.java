package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Resolves the deliberately narrow authorization scope used by client portal sessions. */
public interface ClientSessionAccessPort {
    Optional<ClientSessionAccess> findByUserAndOrganization(UUID userId, UUID organizationId);
    Optional<ClientSessionAccess> findByEmailAndOrganizationSlug(String normalizedEmail, String normalizedSlug);

    record ClientSessionAccess(UUID organizationId, String organizationName, String organizationSlug,
                               UUID clientUserId, UUID clientId, String clientName, String clientRole,
                               UserAccount user, Set<String> permissions) { }
}
