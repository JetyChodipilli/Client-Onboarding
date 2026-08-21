package com.brainserve.onboarding.auth.api.response;
public record MfaSetupResponse(String secret, String otpauthUri) {}
