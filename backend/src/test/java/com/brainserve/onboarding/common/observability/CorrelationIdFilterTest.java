package com.brainserve.onboarding.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeRequestAndCorrelationIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_HEADER, "req-123");
        request.addHeader(CorrelationIdFilter.CORRELATION_HEADER, "corr-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();
        AtomicReference<String> correlationIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            requestIdInsideChain.set(MDC.get(RequestContext.REQUEST_ID_KEY));
            correlationIdInsideChain.set(MDC.get(RequestContext.CORRELATION_ID_KEY));
        });

        assertThat(response.getHeader(CorrelationIdFilter.REQUEST_HEADER)).isEqualTo("req-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_HEADER)).isEqualTo("corr-456");
        assertThat(requestIdInsideChain).hasValue("req-123");
        assertThat(correlationIdInsideChain).hasValue("corr-456");
        assertThat(MDC.get(RequestContext.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(RequestContext.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    void replacesUnsafeExternalIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_HEADER, "bad id with spaces and newline\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(CorrelationIdFilter.REQUEST_HEADER);
        assertThat(generated).isNotBlank().doesNotContain(" ", "\n");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_HEADER)).isEqualTo(generated);
    }
}
