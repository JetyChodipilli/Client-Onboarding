package com.brainserve.onboarding.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {
    @Test void apiResponsesAreNonCacheableAndHardened() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/snapshot");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Strict-Transport-Security")).contains("31536000");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
    }
}
