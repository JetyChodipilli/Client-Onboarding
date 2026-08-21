package com.brainserve.onboarding.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.auth.application.service.PasswordPolicy;
import com.brainserve.onboarding.common.error.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongNonAccountSpecificPassphrase() {
        assertThatCode(() -> policy.validate("Velvet-River-84-Comet", "alex@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortCommonAndEmailDerivedPasswords() {
        assertThatThrownBy(() -> policy.validate("short", "alex@example.com")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("password123!", "alex@example.com")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("alex-super-secure-2026", "alex@example.com")).isInstanceOf(ApiException.class);
    }
}
