package com.brainserve.onboarding.organization.api.response;
import java.util.List;
import java.util.UUID;
public record OrganizationUserResponse(UUID membershipId, UUID userId, String email, String displayName,
                                       String status, List<RoleSummary> roles, long version) {
    public OrganizationUserResponse { roles = List.copyOf(roles); }
    public record RoleSummary(UUID id, String code, String name) {}
}
