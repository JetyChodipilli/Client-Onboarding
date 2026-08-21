package com.brainserve.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationApplicationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("client_onboarding")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.require-explicit-secrets", () -> "false");
        registry.add("app.security.secure-cookies", () -> "false");
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void cleanDatabaseMigratesAndHealthEndpointsAreAvailable() throws IOException, InterruptedException {
        HttpResponse<String> live = get("/health/live");
        HttpResponse<String> ready = get("/health/ready");

        assertThat(live.statusCode()).isEqualTo(200);
        assertThat(live.body()).contains("UP");
        assertThat(ready.statusCode()).isEqualTo(200);
        assertThat(ready.body()).contains("UP");
    }

    @Test
    void unknownApplicationRouteIsDeniedByDefault() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/api/v1/not-implemented");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.body()).contains("\"requestId\"");
    }

    @Test
    void operationalAndDocumentationEndpointsAreNotAnonymousByDefault() throws IOException, InterruptedException {
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(401);
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
