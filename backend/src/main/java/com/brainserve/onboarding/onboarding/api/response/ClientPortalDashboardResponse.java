package com.brainserve.onboarding.onboarding.api.response;
import java.util.List;
public record ClientPortalDashboardResponse(String organizationName, ClientPortalNextActionResponse nextAction,
                                            List<ClientPortalProjectSummaryResponse> projects) {
    public ClientPortalDashboardResponse { projects=List.copyOf(projects); }
}
