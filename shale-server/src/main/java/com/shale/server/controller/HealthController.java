package com.shale.server.controller;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shale.server.health.AppDatabaseHealthCheck;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "Deployment health checks")
public final class HealthController {
    private final ObjectProvider<AppDatabaseHealthCheck> databaseHealthCheck;

    @Autowired
    public HealthController(ObjectProvider<AppDatabaseHealthCheck> databaseHealthCheck) {
        this.databaseHealthCheck = databaseHealthCheck;
    }

    public HealthController() {
        this.databaseHealthCheck = null;
    }

    @Operation(summary = "Application health", description = "DB-free liveness check for App Service probes.")
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @Operation(summary = "Database health", description = "Safe readiness check for the configured auth/app database pool. Does not expose connection details.")
    @GetMapping("/api/health/db")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        AppDatabaseHealthCheck activeHealthCheck = databaseHealthCheck == null ? null : databaseHealthCheck.getIfAvailable();
        if (activeHealthCheck == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "unavailable", "message", "database configuration is not active"));
        }
        if (activeHealthCheck.isReady()) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "unavailable", "message", "database health check failed"));
    }
}
