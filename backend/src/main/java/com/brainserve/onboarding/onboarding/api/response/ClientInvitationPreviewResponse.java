package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import java.time.Instant;
import java.util.UUID;

public record ClientInvitationPreviewResponse(UUID invitationId, String organizationName, String organizationSlug,
        String projectName, String contactName, String email, ClientProjectAccessLevel accessLevel,
        Instant expiresAt, boolean existingAccount) {}
