package com.brainserve.onboarding.forms.api.response;

import java.util.UUID;

public record PublishedFormVersionResponse(UUID formId, String formName, UUID versionId, int versionNumber, int fieldCount) {}
