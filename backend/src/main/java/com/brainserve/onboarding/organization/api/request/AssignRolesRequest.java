package com.brainserve.onboarding.organization.api.request;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import java.util.UUID;
public record AssignRolesRequest(@NotEmpty Set<UUID> roleIds, @PositiveOrZero long version) {
    public AssignRolesRequest { roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds); }
}
