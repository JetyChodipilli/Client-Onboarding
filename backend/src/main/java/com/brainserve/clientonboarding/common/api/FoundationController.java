package com.brainserve.clientonboarding.common.api;

import com.brainserve.clientonboarding.common.observability.RequestIds;
import io.swagger.v3.oas.annotations.Operation;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FoundationController {

    private static final Logger log = LoggerFactory.getLogger(FoundationController.class);

    private final DataSource dataSource;

    public FoundationController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/v1/platform/info")
    @Operation(summary = "Return non-sensitive foundation metadata")
    ApiSuccess<PlatformInfo> platformInfo() {
        return ApiSuccess.of(
                new PlatformInfo(
                        "Client Onboarding Platform",
                        "PHASE_3",
                        "MODULAR_MONOLITH",
                        Map.of("liveness", "/health/live", "readiness", "/health/ready")
                ),
                RequestIds.currentRequestId()
        );
    }

    @GetMapping("/health/live")
    @Operation(summary = "Process liveness")
    ApiSuccess<Map<String, String>> live() {
        return ApiSuccess.of(Map.of("status", "UP"), RequestIds.currentRequestId());
    }

    @GetMapping("/health/ready")
    @Operation(summary = "Critical dependency readiness")
    ResponseEntity<?> ready() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(ApiSuccess.of(
                        Map.of("status", "UP", "database", "UP"),
                        RequestIds.currentRequestId()));
            }
        } catch (SQLException exception) {
            log.warn("Readiness database probe failed", exception);
        }

        ApiFailure failure = ApiFailure.of(
                ApiError.of("DEPENDENCY_UNAVAILABLE", "A critical dependency is unavailable."),
                RequestIds.currentRequestId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(failure);
    }

    public record PlatformInfo(
            String name,
            String phase,
            String architecture,
            Map<String, String> healthEndpoints
    ) {
    }
}
