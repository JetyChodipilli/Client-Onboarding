package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.common.error.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_BUCKETS = 10_000;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String sourceKey, String subjectKey) {
        Instant now = clock.instant();
        checkKey("source:" + sourceKey, properties.loginMaxAttempts() * 10, now);
        checkKey("subject:" + subjectKey, properties.loginMaxAttempts() * 2, now);
    }

    public void failure(String sourceKey, String subjectKey) {
        Instant now = clock.instant();
        increment("source:" + sourceKey, now);
        increment("subject:" + subjectKey, now);
        if (buckets.size() > MAX_BUCKETS) {
            buckets.entrySet().removeIf(entry -> entry.getValue().windowStarted
                    .isBefore(now.minus(properties.loginLockDuration().multipliedBy(2))));
        }
    }

    public void success(String subjectKey) {
        buckets.remove("subject:" + subjectKey);
    }

    private void checkKey(String key, int maximum, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket == null || bucket.windowStarted.isBefore(now.minus(properties.loginLockDuration()))) return;
        if (bucket.attempts >= maximum) {
            throw new DomainException("AUTH_RATE_LIMITED",
                    "Too many sign-in attempts. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void increment(String key, Instant now) {
        if (buckets.size() >= MAX_BUCKETS && !buckets.containsKey(key)) return;
        buckets.compute(key, (ignored, current) -> current == null
                || current.windowStarted.isBefore(now.minus(properties.loginLockDuration()))
                ? new Bucket(1, now) : new Bucket(current.attempts + 1, current.windowStarted));
    }

    private record Bucket(int attempts, Instant windowStarted) { }
}
