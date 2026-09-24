package com.brainserve.clientonboarding.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.clientonboarding.ClientOnboardingApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ApplicationRuntimeSmokeTest {

    @Test
    void startsAgainstPostgresAndServesFoundationEndpoints() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setServerConfig("unix_socket_directories", "")
                .start();
             ConfigurableApplicationContext context = new SpringApplicationBuilder(
                     ClientOnboardingApplication.class)
                     .web(WebApplicationType.SERVLET)
                     .run(
                             "--server.port=0",
                             "--spring.datasource.url="
                                     + postgres.getJdbcUrl("postgres", "postgres"),
                             "--spring.datasource.username=postgres",
                             "--spring.datasource.password=",
                             "--app.security.mfa-encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                             "--spring.jpa.hibernate.ddl-auto=none")) {
            Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            assertThat(port).isNotNull().isPositive();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String requestId = UUID.randomUUID().toString();

            HttpResponse<String> liveness = get(client, port, "/health/live", requestId);
            HttpResponse<String> readiness = get(client, port, "/health/ready", requestId);
            HttpResponse<String> platform = get(client, port, "/api/v1/platform/info", requestId);
            HttpResponse<String> openApi = get(client, port, "/v3/api-docs", requestId);

            assertThat(liveness.statusCode()).isEqualTo(200);
            assertThat(readiness.statusCode()).isEqualTo(200);
            assertThat(readiness.body()).contains("\"database\":\"UP\"");
            assertThat(platform.statusCode()).isEqualTo(200);
            assertThat(platform.body()).contains("\"phase\":\"PHASE_5\"");
            assertThat(platform.headers().firstValue("X-Request-ID")).contains(requestId);
            assertThat(openApi.statusCode()).isEqualTo(200);
            assertThat(openApi.body()).contains("/api/v1/platform/info", "/api/v1/forms", "/api/v1/form-responses/{stepId}/review");
        }
    }

    private HttpResponse<String> get(
            HttpClient client,
            int port,
            String path,
            String requestId
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Request-ID", requestId)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
