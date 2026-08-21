package com.brainserve.onboarding.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.BrowserOriginPolicy;
import com.brainserve.onboarding.common.security.SecurityProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BrowserOriginPolicyTest {
    private final BrowserOriginPolicy policy = new BrowserOriginPolicy(new SecurityProperties(
            List.of("https://portal.example.com"), false, null, null, false,
            Duration.ofMinutes(15), Duration.ofDays(14), Duration.ofMinutes(30), Duration.ofHours(24),
            Duration.ofMinutes(5), Duration.ofHours(72), true, 5, Duration.ofMinutes(15), "issuer", "audience"));

    @Test
    void allowsConfiguredBrowserOriginAndNativeClientWithoutBrowserFetchMetadata() {
        MockHttpServletRequest browser = new MockHttpServletRequest();
        browser.addHeader("Origin", "https://portal.example.com");
        assertThatCode(() -> policy.requireTrustedOriginForCookieCommand(browser)).doesNotThrowAnyException();

        assertThatCode(() -> policy.requireTrustedOriginForCookieCommand(new MockHttpServletRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossOriginAndSameSiteSiblingRequests() {
        MockHttpServletRequest sibling = new MockHttpServletRequest();
        sibling.addHeader("Origin", "https://untrusted.example.com");
        sibling.addHeader("Sec-Fetch-Site", "same-site");
        assertThatThrownBy(() -> policy.requireTrustedOriginForCookieCommand(sibling))
                .isInstanceOf(ApiException.class);

        MockHttpServletRequest missingOrigin = new MockHttpServletRequest();
        missingOrigin.addHeader("Sec-Fetch-Site", "cross-site");
        assertThatThrownBy(() -> policy.requireTrustedOriginForCookieCommand(missingOrigin))
                .isInstanceOf(ApiException.class);
    }
}
