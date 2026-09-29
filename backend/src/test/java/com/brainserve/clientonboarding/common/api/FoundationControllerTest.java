package com.brainserve.clientonboarding.common.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationControllerTest extends com.brainserve.clientonboarding.infrastructure.PostgresTestDatabase {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesFoundationMetadataWithStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/platform/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phase").value("PHASE_6"))
                .andExpect(jsonPath("$.data.architecture").value("MODULAR_MONOLITH"))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_PATTERN)))
                .andExpect(header().string("X-Request-ID", matchesPattern(UUID_PATTERN)))
                .andExpect(header().string("X-Correlation-ID", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void propagatesValidCorrelationIds() throws Exception {
        String requestId = "30b80d34-2ddb-43dc-8578-f24484c849e4";
        String correlationId = "76885f44-115a-4982-a06d-19a04e7c4ed4";

        mockMvc.perform(get("/health/live")
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", requestId))
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void rejectsUndocumentedRoutesByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/unimplemented"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replacesNonCanonicalUuidHeaders() throws Exception {
        mockMvc.perform(get("/health/live").header("X-Request-ID", "1-1-1-1-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", matchesPattern(UUID_PATTERN)));
    }
}
