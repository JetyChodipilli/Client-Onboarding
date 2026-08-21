package com.brainserve.onboarding.organization.api.response;
import java.util.UUID;
public record PermissionResponse(UUID id, String code, String category, String description) {}
