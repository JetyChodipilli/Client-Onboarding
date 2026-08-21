package com.brainserve.onboarding.onboarding.api.request;

import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateClientInvitationRequest(@NotNull UUID contactId, @NotNull ClientProjectAccessLevel accessLevel) {}
