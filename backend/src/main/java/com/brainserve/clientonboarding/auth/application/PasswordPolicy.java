package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.common.error.DomainException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new DomainException("PASSWORD_POLICY_FAILED",
                    "Use 12–72 bytes with uppercase, lowercase, and a number.", HttpStatus.BAD_REQUEST);
        }
    }
}
