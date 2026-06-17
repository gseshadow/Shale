package com.shale.server.controller;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shale.data.config.DataSources;

@RestController
public final class HealthController {
    private final ObjectProvider<DataSources> dataSources;

    public HealthController(ObjectProvider<DataSources> dataSources) {
        this.dataSources = dataSources;
    }

    public HealthController() {
        this.dataSources = null;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/health/db")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        if (dataSources == null || dataSources.getIfAvailable() == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "unavailable", "message", "database configuration is not active"));
        }

        try (Connection connection = dataSources.getObject().auth().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (RuntimeException | java.sql.SQLException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "unavailable", "message", "database health check failed"));
        }
    }
}
