package com.brainserve.onboarding.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.auth.application.service.TotpService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    @Test
    void verifiesKnownRfc6238DerivedSixDigitCode() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        TotpService service = new TotpService(clock);

        // RFC 6238 SHA-1 test secret: "12345678901234567890" in Base32.
        assertThat(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082")).isTrue();
        assertThat(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287083")).isFalse();
    }
}
