package com.brainserve.onboarding.assets.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompleteAssetUploadRequest(@NotNull UUID assetVersionId) {}
