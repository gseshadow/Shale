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
                .andExpect(jsonPath("$.paths['/api/notifications/unread']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/unread-count']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/{notificationId}/activation-target']").exists());
    }

    @Test
    void openApiDocumentsAuthenticatedApplicationReleaseContractsAndClosedVocabularies() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.parameters[?(@.name == 'channel')]").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.parameters[?(@.name == 'after')].description")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("strict canonical major.minor.build"))))
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.responses['200'].content['application/json'].schema.items.$ref")
                        .value(org.hamcrest.Matchers.endsWith("/ApplicationReleaseResponse")))
                .andExpect(jsonPath("$.paths['/api/application-releases/policy/current'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases/policy/current'].get.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.ApplicationReleaseResponse.properties.items.items.$ref")
                        .value(org.hamcrest.Matchers.endsWith("/ApplicationReleaseItemResponse")))
                .andExpect(jsonPath("$.components.schemas.ReleaseChannel.enum")
                        .value(org.hamcrest.Matchers.contains("PRODUCTION", "PILOT", "DEVELOPMENT")))
                .andExpect(jsonPath("$.components.schemas.ReleaseItemType.enum")
                        .value(org.hamcrest.Matchers.contains("FEATURE", "FIX", "IMPROVEMENT", "IMPORTANT", "LINK", "VIDEO")))
                .andExpect(jsonPath("$.components.schemas.ApplicationAccessMode.enum")
                        .value(org.hamcrest.Matchers.contains("NORMAL", "READ_ONLY", "MAINTENANCE", "BLOCKED")));
    }
}
