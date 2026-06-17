package com.shale.server.controller;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shale.server.health.AppDatabaseHealthCheck;

@RestController
public final class HealthController {
    private final ObjectProvider<AppDatabaseHealthCheck> databaseHealthCheck;

    @Autowired
    public HealthController(ObjectProvider<AppDatabaseHealthCheck> databaseHealthCheck) {
        this.databaseHealthCheck = databaseHealthCheck;
    }

    public HealthController() {
        this.databaseHealthCheck = null;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

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
