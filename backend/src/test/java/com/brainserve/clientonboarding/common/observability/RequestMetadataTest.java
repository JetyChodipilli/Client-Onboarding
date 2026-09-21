package com.brainserve.clientonboarding.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestMetadataTest {

    @Test
    void ignoresUntrustedForwardedAddressHeaders() throws Exception {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        RequestMetadata metadata = RequestMetadata.from(request);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("203.0.113.42".getBytes(StandardCharsets.UTF_8)));
        assertThat(metadata.ipHash()).isEqualTo(expected);
    }
}
