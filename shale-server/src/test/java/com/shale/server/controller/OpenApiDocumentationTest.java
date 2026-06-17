package com.shale.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.shale.server.ShaleServerApplication;

@SpringBootTest(classes = ShaleServerApplication.class)
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentsBearerAuthAndCoreWebEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/health']").exists())
                .andExpect(jsonPath("$.paths['/api/health/db']").exists())
                .andExpect(jsonPath("$.paths['/api/cases/search']").exists())
                .andExpect(jsonPath("$.paths['/api/cases/search-page']").exists())
                .andExpect(jsonPath("$.paths['/api/contacts/search']").exists())
                .andExpect(jsonPath("$.paths['/api/contacts/search-page']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/unread']").exists());
    }
}
