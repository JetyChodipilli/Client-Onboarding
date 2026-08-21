package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssetPolicy {
    public static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;
    public static final long ABSOLUTE_MAX_BYTES = 1024L * 1024 * 1024;
    public static final Set<String> DEFAULT_MIMES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp", "video/mp4", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final ObjectMapper mapper;
    public AssetPolicy(ObjectMapper mapper) { this.mapper = mapper; }

    public Policy fromConfiguration(JsonNode configuration) {
        JsonNode config = configuration == null || configuration.isNull() ? mapper.createObjectNode() : configuration;
        if (!config.isObject()) throw invalid("Asset configuration must be a JSON object.");
        long max = config.path("maxFileSizeBytes").asLong(DEFAULT_MAX_BYTES);
        if (max <= 0 || max > ABSOLUTE_MAX_BYTES) throw invalid("maxFileSizeBytes must be between 1 byte and 1 GiB.");
        Set<String> mimes = new LinkedHashSet<>();
        JsonNode allowed = config.get("allowedMimeTypes");
        if (allowed == null || allowed.isNull()) {
            mimes.addAll(DEFAULT_MIMES);
        } else {
            if (!allowed.isArray() || allowed.isEmpty() || allowed.size() > 40) throw invalid("allowedMimeTypes must contain 1-40 MIME types.");
            for (JsonNode item : allowed) {
                if (!item.isTextual()) throw invalid("allowedMimeTypes must contain text values.");
                String mime = normalizeMime(item.asText());
                if (mime.equals("image/svg+xml") || mime.equals("text/html") || mime.equals("application/xhtml+xml")) {
                    throw invalid("Active web-content MIME types are not permitted as project assets.");
                }
                mimes.add(mime);
            }
        }
        return new Policy(max, Set.copyOf(mimes), toJson(mimes));
    }

    public void validateRequestedFile(Policy policy, String declaredMime, long sizeBytes) {
        String mime = normalizeMime(declaredMime);
        if (sizeBytes <= 0 || sizeBytes > policy.maxFileSizeBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSET_FILE_TOO_LARGE", "The selected file exceeds the configured size limit.");
        }
        if (!policy.allowedMimeTypes().contains(mime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSET_MIME_NOT_ALLOWED", "The selected file type is not allowed for this requirement.");
        }
    }

    public void validateDetectedFile(Policy policy, String declaredMime, String detectedMime, long actualSize, String expectedSha, String actualSha) {
        String declared = normalizeMime(declaredMime);
        String detected = normalizeMime(detectedMime);
        if (actualSize <= 0 || actualSize > policy.maxFileSizeBytes()) throw rejected("Uploaded file exceeds the configured size limit.");
        if (!policy.allowedMimeTypes().contains(detected)) throw rejected("File signature identifies a type that is not allowed for this requirement.");
        if (!sameMime(declared, detected)) throw rejected("Declared MIME type does not match the file signature.");
        if (expectedSha != null && !expectedSha.equalsIgnoreCase(actualSha)) throw rejected("Uploaded file checksum does not match the requested file.");
    }

    public static String normalizeMime(String value) {
        if (value == null || value.isBlank()) throw invalid("MIME type is required.");
        String mime = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mime.length() > 160 || !mime.matches("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+")) throw invalid("MIME type is invalid.");
        if (mime.equals("image/jpg")) return "image/jpeg";
        return mime;
    }

    private ArrayNode toJson(Set<String> values) {
        ArrayNode array = mapper.createArrayNode();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private static boolean sameMime(String left, String right) { return normalizeMime(left).equals(normalizeMime(right)); }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "ASSET_POLICY_INVALID", message); }
    private static ApiException rejected(String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_CONTENT_REJECTED", message); }

    public record Policy(long maxFileSizeBytes, Set<String> allowedMimeTypes, JsonNode allowedMimeTypesJson) {}
}
