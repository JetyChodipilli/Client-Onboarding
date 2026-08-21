package com.brainserve.onboarding.assets.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;

final class AssetRequirementJson {
    private AssetRequirementJson() {}
    static Set<String> mimeSet(JsonNode value) {
        Set<String> result = new LinkedHashSet<>();
        value.forEach(item -> result.add(AssetPolicy.normalizeMime(item.asText())));
        return Set.copyOf(result);
    }
}
