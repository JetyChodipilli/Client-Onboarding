package com.brainserve.clientonboarding.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    private final TotpService service = new TotpService();

    @Test
    void acceptsCurrentCodeOnceAndRejectsReplay() {
        String secret = service.newSecret();
        Instant instant = Instant.parse("2026-08-20T12:00:00Z");
        String code = service.generateCode(secret, instant);
        Long step = service.verify(secret, code, instant, null);

        assertThat(step).isNotNull();
        assertThat(service.verify(secret, code, instant, step)).isNull();
    }
}
