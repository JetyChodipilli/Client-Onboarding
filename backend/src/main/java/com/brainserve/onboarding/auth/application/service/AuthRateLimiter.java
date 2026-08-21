package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.util.CryptoSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 12;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthRateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Uses an independent transaction so rejected authentication requests cannot roll the counter back.
     * The insert-first pattern also closes the missing-row race: a concurrent insert blocks on the
     * unique key, then the losing request locks and increments the committed row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void consume(String scope, String keyMaterial) {
        String keyHash = CryptoSupport.sha256Hex(keyMaterial);
        Instant now = clock.instant();

        int inserted = jdbc.update("""
                INSERT INTO client_onboarding.auth_rate_limits
                    (key_hash, scope, window_started_at, attempts, blocked_until, updated_at)
                VALUES (?, ?, ?, 1, NULL, ?)
                ON CONFLICT (key_hash, scope) DO NOTHING
                """, keyHash, scope, now, now);
        if (inserted == 1) return;

        RateRow row = jdbc.query("""
                SELECT window_started_at, attempts, blocked_until
                FROM client_onboarding.auth_rate_limits
                WHERE key_hash = ? AND scope = ?
                FOR UPDATE
                """, rs -> rs.next() ? new RateRow(
                        rs.getTimestamp(1).toInstant(), rs.getInt(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()) : null,
                keyHash, scope);
        if (row == null) {
            // Defensive only: the unique-key insert/select sequence should make this unreachable.
            throw new IllegalStateException("Authentication rate-limit row disappeared during update");
        }
        if (row.blockedUntil() != null && row.blockedUntil().isAfter(now)) throw limited();

        if (!row.windowStartedAt().plus(WINDOW).isAfter(now)) {
            jdbc.update("""
                    UPDATE client_onboarding.auth_rate_limits
                    SET window_started_at = ?, attempts = 1, blocked_until = NULL, updated_at = ?
                    WHERE key_hash = ? AND scope = ?
                    """, now, now, keyHash, scope);
            return;
        }

        int attempts = row.attempts() + 1;
        Instant blockedUntil = attempts >= MAX_ATTEMPTS ? now.plus(BLOCK) : null;
        jdbc.update("""
                UPDATE client_onboarding.auth_rate_limits
                SET attempts = ?, blocked_until = ?, updated_at = ?
                WHERE key_hash = ? AND scope = ?
                """, attempts, blockedUntil, now, keyHash, scope);
        if (blockedUntil != null) throw limited();
    }

    private static ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED",
                "Too many authentication attempts. Try again later.");
    }

    private record RateRow(Instant windowStartedAt, int attempts, Instant blockedUntil) {}
}
