package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    private static final Set<String> BLOCKED = Set.of(
            "password123!", "qwerty123456", "letmein123456", "admin123456!", "welcome123456!");

    public void validate(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw invalid("Password must be between 12 and 128 characters.");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(normalized)) throw invalid("Choose a less common password.");
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (local.length() >= 4 && normalized.contains(local)) {
                throw invalid("Password must not contain your email name.");
            }
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_FAILED", message);
    }
}
