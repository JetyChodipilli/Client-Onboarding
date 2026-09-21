package com.brainserve.clientonboarding.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.clientonboarding.common.error.DomainException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("CorrectHorse7Battery")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortAndOversizedBcryptInputs() {
        assertThatThrownBy(() -> policy.validate("short7A")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> policy.validate("A1a" + "x".repeat(70))).isInstanceOf(DomainException.class);
    }
}
