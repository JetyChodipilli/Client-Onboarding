package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;import java.util.*;
public record CreateContractRequest(@NotNull UUID projectId,UUID stepId,UUID templateVersionId,@Size(max=240) String subject,Instant expiresAt,@NotEmpty @Size(max=20) List<UUID> recipientContactIds){public CreateContractRequest{recipientContactIds=recipientContactIds==null?List.of():List.copyOf(recipientContactIds);}}
