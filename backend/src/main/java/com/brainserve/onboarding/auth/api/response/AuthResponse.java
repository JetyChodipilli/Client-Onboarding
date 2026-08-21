package com.brainserve.onboarding.auth.api.response;
import java.time.Instant;
public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, AuthUserResponse user,
                           boolean mfaRequired, boolean mfaSetupRequired, String challengeToken) {
    public static AuthResponse authenticated(String accessToken, Instant expiresAt, AuthUserResponse user) {
        return new AuthResponse(accessToken, expiresAt, user, false, false, null);
    }
    public static AuthResponse mfaChallenge(String token) {
        return new AuthResponse(null, null, null, true, false, token);
    }
    public static AuthResponse mfaSetup(String token) {
        return new AuthResponse(null, null, null, false, true, token);
    }
}
