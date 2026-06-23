package com.shale.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.server.health.AppDatabaseHealthCheck;

class HealthControllerTest {

    @Test
    void healthDoesNotRequireDatabaseConfiguration() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController())
                .build();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void databaseHealthReturnsOkWhenAuthPoolSelectOneSucceeds() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(fixedProvider(() -> true)))
                .build();

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void databaseHealthReturnsSafeUnavailableWhenAuthPoolFails() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(fixedProvider(() -> false)))
                .build();

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.message").value("database health check failed"));
    }

    private static ObjectProvider<AppDatabaseHealthCheck> fixedProvider(AppDatabaseHealthCheck healthCheck) {
        return new ObjectProvider<>() {
            @Override
            public AppDatabaseHealthCheck getObject(Object... args) {
                return healthCheck;
            }

            @Override
            public AppDatabaseHealthCheck getIfAvailable() {
                return healthCheck;
            }

            @Override
            public AppDatabaseHealthCheck getIfUnique() {
                return healthCheck;
            }

            @Override
            public AppDatabaseHealthCheck getObject() {
                return healthCheck;
            }
        };
    }
}
