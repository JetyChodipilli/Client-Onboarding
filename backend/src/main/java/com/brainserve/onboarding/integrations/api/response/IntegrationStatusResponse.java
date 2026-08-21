package com.brainserve.onboarding.integrations.api.response;

import java.util.List;

public record IntegrationStatusResponse(List<IntegrationStatusItem> integrations) {
    public IntegrationStatusResponse { integrations = List.copyOf(integrations); }
    public record IntegrationStatusItem(String key, String name, String category, String status,
                                        String provider, boolean enabled, boolean productionReady,
                                        String message) {}
}
