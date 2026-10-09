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
@org.springframework.context.annotation.Import(OpenApiDocumentationTest.SchemaOnlyServices.class)
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.context.TestConfiguration
    static class SchemaOnlyServices {
        // The default profile omits this runtime bean, while its controllers remain schema owners.
        @org.springframework.context.annotation.Bean
        com.shale.server.runtime.SessionManagementService schemaOnlySessions() {
            return new com.shale.server.runtime.SessionManagementService(principal -> {
                throw new AssertionError("OpenAPI generation must not open runtime database connections");
            });
        }
    }


    @Test
    void legacyCaseSearchPageKeepsParametersDtoAndFourFieldPageSchema() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        var operation = root.path("paths").path("/api/cases/search-page").path("get");
        org.junit.jupiter.api.Assertions.assertFalse(operation.isMissingNode());
        java.util.Map<String, com.fasterxml.jackson.databind.JsonNode> params = new java.util.HashMap<>();
        operation.path("parameters").forEach(parameter -> params.put(parameter.path("name").asText(), parameter));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("query", "page", "size"), params.keySet());
        org.junit.jupiter.api.Assertions.assertEquals("", params.get("query").path("schema").path("default").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, params.get("page").path("schema").path("default").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(25, params.get("size").path("schema").path("default").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(operation.path("security").get(0).has("bearerAuth"));
        var content = operation.path("responses").path("200").path("content");
        var pageRef = content.elements().next().path("schema").path("$ref").asText();
        org.junit.jupiter.api.Assertions.assertTrue(pageRef.startsWith("#/components/schemas/"));
        var page = root.path("components").path("schemas").path(pageRef.substring(pageRef.lastIndexOf('/') + 1));
        java.util.Set<String> fields = new java.util.HashSet<>();
        page.path("properties").fieldNames().forEachRemaining(fields::add);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("items", "page", "size", "total"), fields,
                "R1 must retain the legacy page schema without count/hasMore");
        org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/CaseOverviewDto",
                page.path("properties").path("items").path("items").path("$ref").asText());
        org.junit.jupiter.api.Assertions.assertTrue(root.path("components").path("schemas")
                .path("CaseOverviewDto").path("properties").has("description"), "Legacy broad DTO is preserved");
    }

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
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.parameters[?(@.name == 'channel')]").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.parameters[?(@.name == 'after')].description")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("strict canonical major.minor.build"))))
                .andExpect(jsonPath("$.paths['/api/application-releases'].get.responses['200'].content['*/*'].schema.items.$ref")
                        .value(org.hamcrest.Matchers.endsWith("/ApplicationReleaseResponse")))
                .andExpect(jsonPath("$.paths['/api/application-releases/policy/current'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/application-releases/policy/current'].get.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.ApplicationReleaseResponse.properties.items.items.$ref")
                        .value(org.hamcrest.Matchers.endsWith("/ApplicationReleaseItemResponse")))
                .andReturn().getResponse().getContentAsString();
        var schemas = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("components").path("schemas");
        // Existing DTO annotations generate inline enums (including duplicate allowableValues), not enum $refs.
        assertClosedEnum(schemas.path("ApplicationReleaseResponse").path("properties").path("channel"),
                java.util.Set.of("PRODUCTION", "PILOT", "DEVELOPMENT"));
        assertClosedEnum(schemas.path("ApplicationReleaseItemResponse").path("properties").path("type"),
                java.util.Set.of("FEATURE", "FIX", "IMPROVEMENT", "IMPORTANT", "LINK", "VIDEO"));
        assertClosedEnum(schemas.path("ApplicationPolicyResponse").path("properties").path("accessMode"),
                java.util.Set.of("NORMAL", "READ_ONLY", "MAINTENANCE", "BLOCKED"));
    }

    private static void assertClosedEnum(com.fasterxml.jackson.databind.JsonNode property, java.util.Set<String> expected) {
        java.util.Set<String> actual = new java.util.HashSet<>();
        property.path("enum").forEach(value -> actual.add(value.asText()));
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, "Generated enum must have exactly the approved vocabulary");
    }
}
