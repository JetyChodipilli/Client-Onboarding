package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Security policy for platform-access guidance and client submissions.
 * The product coordinates access grants; it never acts as a credential vault.
 */
@Component
public class PlatformAccessSafetyPolicy {
    private static final int MAX_RESOURCES = 12;
    private static final Pattern CREDENTIAL_REQUEST = Pattern.compile(
            "(?i)\\b(send|share|provide|paste|enter|submit|give|upload)\\b.{0,40}\\b(password|passcode|secret|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|private[ _-]?key|recovery[ _-]?code)\\b");
    private static final Pattern CREDENTIAL_VALUE = Pattern.compile(
            "(?i)(\\bpassword\\s*[:=]|\\bpwd\\s*[:=]|\\bsecret\\s*[:=]|\\bapi[ _-]?key\\s*[:=]|\\baccess[ _-]?token\\s*[:=]|\\brefresh[ _-]?token\\s*[:=]|\\bbearer\\s+[a-z0-9._~+/-]{12,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

    public void validateGuide(String instructions, String helpUrl, JsonNode resources) {
        if (instructions == null || instructions.isBlank()) throw invalid("Access instructions are required.");
        if (CREDENTIAL_REQUEST.matcher(instructions).find()) {
            throw invalid("Access guides must never ask clients to share third-party passwords, tokens, API keys, private keys, passcodes, or recovery codes.");
        }
        validateUrl(helpUrl, "Help URL");
        if (resources == null || !resources.isArray()) throw invalid("Guide resources must be an array.");
        if (resources.size() > MAX_RESOURCES) throw invalid("A guide can contain at most 12 resources.");
        for (JsonNode resource : resources) {
            if (!resource.isObject()) throw invalid("Each guide resource must be an object.");
            String type = text(resource, "type", 24).toUpperCase(Locale.ROOT);
            if (!type.equals("LINK") && !type.equals("VIDEO") && !type.equals("DOCUMENT")) {
                throw invalid("Guide resource type must be LINK, VIDEO, or DOCUMENT.");
            }
            text(resource, "label", 180);
            validateUrl(text(resource, "url", 2000), "Guide resource URL");
            if (resource.size() > 3) throw invalid("Guide resources may contain only type, label, and url.");
        }
    }

    public void validateClientSubmission(String accountIdentifier, String note) {
        rejectCredentialValue(accountIdentifier);
        rejectCredentialValue(note);
    }

    private static void rejectCredentialValue(String value) {
        if (value != null && CREDENTIAL_VALUE.matcher(value).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCESS_CREDENTIAL_REJECTED",
                    "Do not submit passwords, API keys, tokens, private keys, passcodes, or other authentication secrets. Grant access in the third-party platform and provide only a safe account identifier or confirmation note.");
        }
    }

    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid("Guide resource " + field + " is required.");
        String normalized = value.asText().trim();
        if (normalized.length() > max) throw invalid("Guide resource " + field + " is too long.");
        return normalized;
    }

    private static void validateUrl(String value, String label) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean localHttp = "http".equalsIgnoreCase(scheme) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
            if (!("https".equalsIgnoreCase(scheme) || localHttp) || host == null || uri.getUserInfo() != null) {
                throw invalid(label + " must use HTTPS (HTTP is allowed only for localhost development) and must not contain embedded credentials.");
            }
        } catch (IllegalArgumentException ex) {
            throw invalid(label + " is invalid.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PLATFORM_ACCESS_INVALID", message);
    }
}
