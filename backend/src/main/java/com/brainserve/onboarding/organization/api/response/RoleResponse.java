package com.brainserve.onboarding.organization.api.response;
import java.util.Set;
import java.util.UUID;
public record RoleResponse(UUID id, String code, String name, String description, boolean systemRole,
                           Set<String> permissions, long version) { public RoleResponse { permissions = Set.copyOf(permissions); } }
