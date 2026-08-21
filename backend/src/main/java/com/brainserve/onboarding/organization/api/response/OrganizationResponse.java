package com.brainserve.onboarding.organization.api.response;
import java.util.UUID;
public record OrganizationResponse(UUID id, String name, String slug, String status, long version) {}
