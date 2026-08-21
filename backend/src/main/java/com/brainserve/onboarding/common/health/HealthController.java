package com.brainserve.onboarding.common.health;

import io.swagger.v3.oas.annotations.Hidden;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/health")
public class HealthController {

    private final ApplicationAvailability availability;
    private final DataSource dataSource;

    public HealthController(ApplicationAvailability availability, DataSource dataSource) {
        this.availability = availability;
        this.dataSource = dataSource;
    }

    @GetMapping("/live")
    ResponseEntity<Map<String, String>> liveness() {
        boolean up = availability.getLivenessState() == LivenessState.CORRECT;
        return response(up);
    }

    @GetMapping("/ready")
    ResponseEntity<Map<String, String>> readiness() {
        boolean acceptingTraffic = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return response(acceptingTraffic && databaseAvailable());
    }

    private boolean databaseAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static ResponseEntity<Map<String, String>> response(boolean up) {
        Map<String, String> body = Map.of("status", up ? "UP" : "DOWN");
        return up ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }
}
