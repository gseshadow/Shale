package com.shale.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.shale.server.ShaleServerApplication;

@SpringBootTest(classes = ShaleServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(OpenApiDocumentationTest.SchemaOnlyServices.class)
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired private org.springframework.web.context.WebApplicationContext context;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") private int port;

    @Test
    void embeddedServerPreservesPublicHealthAndHasNoDefaultSecurityOrProtocolSurfaces() throws Exception {
        org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("springSecurityFilterChain"));
        org.junit.jupiter.api.Assertions.assertFalse(org.springframework.util.ClassUtils.isPresent(
                "org.springframework.security.web.FilterChainProxy", getClass().getClassLoader()),
                "Crypto must not pull in a servlet security chain");
        org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("userDetailsService"));
        org.junit.jupiter.api.Assertions.assertFalse(context.getServletContext().getFilterRegistrations()
                .containsKey("springSecurityFilterChain"));
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (String path : java.util.List.of("/api/health", "/swagger-ui/index.html", "/login",
                    "/actuator", "/actuator/health", "/oauth/authorize", "/oauth/token",
                    "/.well-known/oauth-authorization-server", "/.well-known/oauth-protected-resource",
                    "/oauth/mcp/authorize", "/oauth/mcp/token", "/oauth/mcp/revoke",
                    "/.well-known/oauth-authorization-server/oauth/mcp", "/.well-known/oauth-protected-resource/mcp", "/mcp")) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path))
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                boolean existingPublicRoute = path.equals("/api/health") || path.equals("/swagger-ui/index.html");
                // Existing generic advice handles unmapped static resources as sanitized HTTP 500.
                // Preserve that fallback; prove absence of handlers rather than inventing a 404 contract.
                org.junit.jupiter.api.Assertions.assertEquals(existingPublicRoute ? 200 : 500, response.statusCode(), path);
                if (!existingPublicRoute) {
                    org.junit.jupiter.api.Assertions.assertEquals("Internal server error.",
                            wireJson.readTree(response.body()).path("message").asText(), path);
                    var handlers = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
                    org.junit.jupiter.api.Assertions.assertTrue(handlers.getHandlerMethods().keySet().stream()
                            .noneMatch(mapping -> mapping.getPatternValues().contains(path)), "Unexpected endpoint: " + path);
                }
                org.junit.jupiter.api.Assertions.assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty(), path);
                org.junit.jupiter.api.Assertions.assertTrue(response.headers().allValues("Set-Cookie").stream()
                        .noneMatch(cookie -> cookie.startsWith("JSESSIONID=")), path);
            }
        }
        mockMvc.perform(get("/api/health").header("Origin", "https://untrusted.invalid"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("WWW-Authenticate"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(false));
    }

    @Test
    void bootMapperRetainsWireDefaultsAndIsTheMvcAndBoundedCaseMapper() throws Exception {
        var adapter = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class);
        var converter = adapter.getMessageConverters().stream()
                .filter(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class::isInstance)
                .map(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class::cast).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertSame(wireJson, converter.getMapper(),
                "MVC and bounded Case byte serialization must share the configured mapper");
        var search = wireJson.readValue("{\"query\":null,\"page\":null,\"unknown\":true}",
                MinimizedCaseReadController.SearchRequest.class);
        org.junit.jupiter.api.Assertions.assertNull(search.query());
        org.junit.jupiter.api.Assertions.assertNull(search.page());
        org.junit.jupiter.api.Assertions.assertNull(search.size());
        var dates = wireJson.readTree(wireJson.writeValueAsBytes(java.util.Map.of(
                "instant", java.time.Instant.parse("2026-10-21T15:25:00Z"),
                "local", java.time.LocalDateTime.parse("2026-10-21T09:25:00"))));
        org.junit.jupiter.api.Assertions.assertEquals("2026-10-21T15:25:00Z", dates.path("instant").asText());
        org.junit.jupiter.api.Assertions.assertEquals("2026-10-21T09:25:00", dates.path("local").asText());
    }


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
    void minimizedReadSchemasAreConcreteBoundedNullableSecuredAndAdditive() throws Exception {
        var root=tools.jackson.databind.json.JsonMapper.builder().build().readTree(mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var schemas=root.path("components").path("schemas");
        for(String path:java.util.List.of("/api/v2/cases/search-page","/api/v2/cases/assigned-page","/api/v2/cases/{caseId}/overview")) {
            String verb=path.endsWith("search-page")?"post":"get";
            var operation=root.path("paths").path(path).path(verb);
            org.junit.jupiter.api.Assertions.assertTrue(operation.path("security").get(0).has("bearerAuth"));
            for(String code:java.util.List.of("200","400","401","403","503"))
                org.junit.jupiter.api.Assertions.assertTrue(operation.path("responses").has(code),path+" requires "+code);
            org.junit.jupiter.api.Assertions.assertTrue(operation.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText().contains("MinimizedCase"));
        }
        var search=root.path("paths").path("/api/v2/cases/search-page");
        org.junit.jupiter.api.Assertions.assertFalse(search.has("get"),"Search text must only occur in the request body");
        org.junit.jupiter.api.Assertions.assertTrue(search.path("post").path("requestBody").path("content").has("application/json"));
        org.junit.jupiter.api.Assertions.assertTrue(root.path("paths").path("/api/v2/cases/{caseId}/overview").path("get").path("responses").has("404"));
        var overview=schemas.path("MinimizedCaseOverview");
        java.util.Set<String> keys=new java.util.HashSet<>();overview.path("properties").propertyNames().forEach(keys::add);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("caseId","caseNumber","caseName","status","practiceArea","responsibleAttorney","primaryLegalAssistant","updatedAt"),keys);
        org.junit.jupiter.api.Assertions.assertEquals(8,overview.path("required").size());
        org.junit.jupiter.api.Assertions.assertEquals(255,overview.path("properties").path("caseName").path("maxLength").asInt());
        for(String field:java.util.List.of("caseNumber","status","practiceArea","responsibleAttorney","primaryLegalAssistant","updatedAt"))
            org.junit.jupiter.api.Assertions.assertTrue(overview.path("properties").path(field).path("nullable").asBoolean(),field+" must be explicitly nullable");
        org.junit.jupiter.api.Assertions.assertEquals(25,schemas.path("MinimizedCasePage").path("properties").path("items").path("maxItems").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(100,schemas.path("MinimizedCasePage").path("properties").path("page").path("maximum").asInt());
        org.junit.jupiter.api.Assertions.assertFalse(schemas.path("MinimizedCasePage").path("properties").has("total"));
        org.junit.jupiter.api.Assertions.assertTrue(schemas.path("CaseOverviewDto").path("properties").has("description"),"Legacy schema remains broad");
    }

    @Test
    void legacyCaseSearchPageKeepsParametersDtoAndFourFieldPageSchema() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        var operation = root.path("paths").path("/api/cases/search-page").path("get");
        org.junit.jupiter.api.Assertions.assertFalse(operation.isMissingNode());
        java.util.Map<String, tools.jackson.databind.JsonNode> params = new java.util.HashMap<>();
        operation.path("parameters").forEach(parameter -> params.put(parameter.path("name").asText(), parameter));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("query", "page", "size"), params.keySet());
        org.junit.jupiter.api.Assertions.assertEquals("", params.get("query").path("schema").path("default").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, params.get("page").path("schema").path("default").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(25, params.get("size").path("schema").path("default").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(operation.path("security").get(0).has("bearerAuth"));
        var content = operation.path("responses").path("200").path("content");
        var pageRef = content.values().iterator().next().path("schema").path("$ref").asText();
        org.junit.jupiter.api.Assertions.assertTrue(pageRef.startsWith("#/components/schemas/"));
        var page = root.path("components").path("schemas").path(pageRef.substring(pageRef.lastIndexOf('/') + 1));
        java.util.Set<String> fields = new java.util.HashSet<>();
        page.path("properties").propertyNames().forEach(fields::add);
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
        var schemas = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).path("components").path("schemas");
        // Existing DTO annotations generate inline enums (including duplicate allowableValues), not enum $refs.
        assertClosedEnum(schemas.path("ApplicationReleaseResponse").path("properties").path("channel"),
                java.util.Set.of("PRODUCTION", "PILOT", "DEVELOPMENT"));
        assertClosedEnum(schemas.path("ApplicationReleaseItemResponse").path("properties").path("type"),
                java.util.Set.of("FEATURE", "FIX", "IMPROVEMENT", "IMPORTANT", "LINK", "VIDEO"));
        assertClosedEnum(schemas.path("ApplicationPolicyResponse").path("properties").path("accessMode"),
                java.util.Set.of("NORMAL", "READ_ONLY", "MAINTENANCE", "BLOCKED"));
    }

    /** Bounded compatibility baseline: wire semantics only, never generated ordering/metadata. */
    @Test
    void stableCaseContractRetainsRoutesInputsTypesRequiredNullabilityAndAuthentication() throws Exception {
        var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var schemas = root.path("components").path("schemas");
        var scheme = root.at("/components/securitySchemes/bearerAuth");
        org.junit.jupiter.api.Assertions.assertEquals("http", scheme.path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("bearer", scheme.path("scheme").asText());
        for (String path : java.util.List.of("/api/v2/cases/search-page", "/api/v2/cases/assigned-page", "/api/v2/cases/{caseId}/overview")) {
            var operation = root.path("paths").path(path).path(path.endsWith("search-page") ? "post" : "get");
            org.junit.jupiter.api.Assertions.assertFalse(operation.isMissingNode(), "Stable route removed: " + path);
            org.junit.jupiter.api.Assertions.assertTrue(operation.path("security").findValues("bearerAuth").size() == 1,
                    "Stable operations must retain bearer authentication: " + path);
            String responseType = path.endsWith("overview") ? "MinimizedCaseOverview" : "MinimizedCasePage";
            org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/" + responseType,
                    operation.at("/responses/200/content/application~1json/schema/$ref").asText());
            for (String code : java.util.List.of("400", "401", "403", "503"))
                org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/ApiErrorResponse",
                        operation.path("responses").path(code).at("/content/application~1json/schema/$ref").asText(), path + " " + code);
        }
        var assigned = root.path("paths").path("/api/v2/cases/assigned-page").path("get");
        java.util.Map<String, tools.jackson.databind.JsonNode> parameters = new java.util.HashMap<>();
        assigned.path("parameters").forEach(p -> parameters.put(p.path("name").asText(), p));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("page", "size"), parameters.keySet());
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            org.junit.jupiter.api.Assertions.assertEquals("query", parameter.path("in").asText());
            org.junit.jupiter.api.Assertions.assertFalse(parameter.path("required").asBoolean());
            assertBounds(parameter.path("schema"), name.equals("page") ? 0 : 1, name.equals("page") ? 100 : 25);
            org.junit.jupiter.api.Assertions.assertEquals(name.equals("page") ? 0 : 25, parameter.at("/schema/default").asInt());
        }
        var overview = root.path("paths").path("/api/v2/cases/{caseId}/overview").path("get");
        var id = overview.path("parameters").get(0);
        org.junit.jupiter.api.Assertions.assertEquals("caseId", id.path("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("path", id.path("in").asText());
        org.junit.jupiter.api.Assertions.assertTrue(id.path("required").asBoolean());
        assertBounds(id.path("schema"), 1, Integer.MAX_VALUE);
        org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/ApiErrorResponse",
                overview.at("/responses/404/content/application~1json/schema/$ref").asText());
        var search = root.path("paths").path("/api/v2/cases/search-page").path("post");
        org.junit.jupiter.api.Assertions.assertTrue(search.at("/requestBody/required").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/SearchRequest",
                search.at("/requestBody/content/application~1json/schema/$ref").asText());
        assertFields(schemas, "SearchRequest", java.util.Map.of("query", "string", "page", "integer", "size", "integer"),
                java.util.Set.of(), java.util.Set.of("query", "page", "size"));
        org.junit.jupiter.api.Assertions.assertFalse(schemas.at("/SearchRequest/properties/query").has("maxLength"),
                "Raw maxLength would reject valid surrounding whitespace; runtime bounds the stripped UTF-16 query");
        org.junit.jupiter.api.Assertions.assertEquals(100, schemas.path("SearchRequest").path("x-max-stripped-query-utf16-length").asInt());
        assertBounds(schemas.at("/SearchRequest/properties/page"), 0, 100);
        assertBounds(schemas.at("/SearchRequest/properties/size"), 1, 25);
        assertFields(schemas, "MinimizedCaseOverview", java.util.Map.of(
                "caseId", "integer", "caseNumber", "string", "caseName", "string", "status", "CaseReadStatus",
                "practiceArea", "CaseReadPracticeArea", "responsibleAttorney", "CaseReadUser",
                "primaryLegalAssistant", "CaseReadUser", "updatedAt", "string"), null,
                java.util.Set.of("caseNumber", "status", "practiceArea", "responsibleAttorney", "primaryLegalAssistant", "updatedAt"));
        assertFields(schemas, "MinimizedCasePage", java.util.Map.of("items", "array", "page", "integer", "size", "integer", "hasMore", "boolean"), null, java.util.Set.of());
        assertFields(schemas, "CaseReadStatus", java.util.Map.of("id", "integer", "name", "string", "color", "string"), null, java.util.Set.of("color"));
        assertFields(schemas, "CaseReadPracticeArea", java.util.Map.of("id", "integer", "name", "string"), null, java.util.Set.of());
        assertFields(schemas, "CaseReadUser", java.util.Map.of("userId", "integer", "displayName", "string"), null, java.util.Set.of());
        assertFields(schemas, "ApiErrorResponse", java.util.Map.of("timestamp", "string", "status", "integer", "error", "string", "message", "string", "path", "string", "fieldErrors", "array"), null, java.util.Set.of());
        org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/MinimizedCaseOverview", schemas.at("/MinimizedCasePage/properties/items/items/$ref").asText());
        assertBounds(schemas.at("/MinimizedCasePage/properties/page"), 0, 100);
        assertBounds(schemas.at("/MinimizedCasePage/properties/size"), 1, 25);
        for (String[] identity : java.util.List.of(new String[]{"MinimizedCaseOverview","caseId"},
                new String[]{"CaseReadStatus","id"},new String[]{"CaseReadPracticeArea","id"},new String[]{"CaseReadUser","userId"}))
            assertBounds(schemas.path(identity[0]).path("properties").path(identity[1]),1,Integer.MAX_VALUE);
        org.junit.jupiter.api.Assertions.assertFalse(schemas.at("/MinimizedCaseOverview/properties/updatedAt").has("format"),
                "Stored local timestamp text must not become an offset/UTC date-time schema");
        for (String[] text : java.util.List.of(new String[]{"MinimizedCaseOverview","caseName"},
                new String[]{"CaseReadStatus","name"},new String[]{"CaseReadPracticeArea","name"},new String[]{"CaseReadUser","displayName"}))
            org.junit.jupiter.api.Assertions.assertEquals(255,schemas.path(text[0]).path("properties").path(text[1]).path("maxLength").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(200,schemas.at("/MinimizedCaseOverview/properties/caseNumber/maxLength").asInt());

    }

    @Autowired private tools.jackson.databind.ObjectMapper wireJson;

    @Test
    void generatedCaseSchemasDescribeActualSerializedRecordsIncludingNullsAndRelationships() throws Exception {
        var root=wireJson.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var empty=new com.shale.core.dto.MinimizedCaseOverview(1,null,"",null,null,null,null,null);
        var populated=new com.shale.core.dto.MinimizedCaseOverview(2147483647,"N", "Synthetic",
                new com.shale.core.dto.MinimizedCaseOverview.CaseReadStatus(2,"Open",null),
                new com.shale.core.dto.MinimizedCaseOverview.CaseReadPracticeArea(3,"Area"),
                new com.shale.core.dto.MinimizedCaseOverview.CaseReadUser(4,"Attorney"),
                new com.shale.core.dto.MinimizedCaseOverview.CaseReadUser(5,"Assistant"),"2026-10-21T09:25");
        var schemas=root.at("/components/schemas");
        for (var item : java.util.List.of(empty,populated)) {
            assertSerialized(schemas,schemas.path("MinimizedCaseOverview"),wireJson.readTree(wireJson.writeValueAsBytes(item)));
            var page=new com.shale.core.dto.MinimizedCasePage(java.util.List.of(item),100,1,true);
            assertSerialized(schemas,schemas.path("MinimizedCasePage"),wireJson.readTree(wireJson.writeValueAsBytes(page)));
        }
        var error=new com.shale.server.dto.ApiErrorResponse(java.time.Instant.parse("2026-10-21T15:25:00Z"),503,
                "case_read_unavailable","Case read unavailable. Retry explicitly.","/api/v2/cases/assigned-page");
        assertSerialized(schemas,schemas.path("ApiErrorResponse"),wireJson.readTree(wireJson.writeValueAsBytes(error)));
    }

    /** Checks the schema features used by these records, rather than snapshots of generated JSON. */
    private static void assertSerialized(tools.jackson.databind.JsonNode schemas,
            tools.jackson.databind.JsonNode schema, tools.jackson.databind.JsonNode value) {
        if (value.isNull()) {
            org.junit.jupiter.api.Assertions.assertTrue(schema.path("nullable").asBoolean(),"Serialized null must be documented");
            return;
        }
        if (schema.has("$ref")) {
            assertSerialized(schemas,schemas.path(schema.path("$ref").asText().replace("#/components/schemas/","")),value);
            return;
        }
        if (schema.has("allOf")) {
            for (var member : schema.path("allOf")) assertSerialized(schemas,member,value);
            return;
        }
        switch (schema.path("type").asText()) {
            case "object" -> {
                org.junit.jupiter.api.Assertions.assertTrue(value.isObject());
                schema.path("required").forEach(field -> org.junit.jupiter.api.Assertions.assertTrue(value.has(field.asText()),"Missing serialized " + field));
                value.properties().forEach(field -> {
                    org.junit.jupiter.api.Assertions.assertTrue(schema.path("properties").has(field.getKey()),"Undocumented serialized member: " + field.getKey());
                    assertSerialized(schemas,schema.path("properties").path(field.getKey()),field.getValue());
                });
            }
            case "array" -> {
                org.junit.jupiter.api.Assertions.assertTrue(value.isArray());
                if (schema.has("maxItems")) org.junit.jupiter.api.Assertions.assertTrue(value.size()<=schema.path("maxItems").asInt());
                value.forEach(item -> assertSerialized(schemas,schema.path("items"),item));
            }
            case "string" -> {
                org.junit.jupiter.api.Assertions.assertTrue(value.isTextual());
                if (schema.has("maxLength")) org.junit.jupiter.api.Assertions.assertTrue(value.asText().length()<=schema.path("maxLength").asInt());
                if (schema.has("pattern")) org.junit.jupiter.api.Assertions.assertTrue(value.asText().matches(schema.path("pattern").asText()));
            }
            case "integer" -> {
                org.junit.jupiter.api.Assertions.assertTrue(value.isIntegralNumber());
                if (schema.has("minimum")) org.junit.jupiter.api.Assertions.assertTrue(value.asLong()>=schema.path("minimum").asLong());
                if (schema.has("maximum")) org.junit.jupiter.api.Assertions.assertTrue(value.asLong()<=schema.path("maximum").asLong());
            }
            case "boolean" -> org.junit.jupiter.api.Assertions.assertTrue(value.isBoolean());
            default -> org.junit.jupiter.api.Assertions.fail("Unhandled selected schema: " + schema);
        }
    }

    private static void assertBounds(tools.jackson.databind.JsonNode schema, int minimum, int maximum) {
        org.junit.jupiter.api.Assertions.assertEquals("integer", schema.path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals(minimum, schema.path("minimum").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(maximum, schema.path("maximum").asInt());
    }

    private static void assertFields(tools.jackson.databind.JsonNode schemas, String name,
            java.util.Map<String, String> types, java.util.Set<String> required, java.util.Set<String> nullable) {
        var schema = schemas.path(name);
        java.util.Set<String> actual = new java.util.HashSet<>();
        schema.path("properties").propertyNames().forEach(actual::add);
        org.junit.jupiter.api.Assertions.assertEquals(types.keySet(), actual, name + " field allowlist changed; review compatibility/disclosure");
        java.util.Set<String> actualRequired = new java.util.HashSet<>();
        schema.path("required").forEach(n -> actualRequired.add(n.asText()));
        org.junit.jupiter.api.Assertions.assertEquals(required == null ? types.keySet() : required, actualRequired, name + " required members");
        types.forEach((field, type) -> {
            var property = schema.path("properties").path(field);
            org.junit.jupiter.api.Assertions.assertEquals(nullable.contains(field), property.path("nullable").asBoolean(), name + "." + field);
            String actualType = property.path("type").asText();
            if (type.startsWith("CaseRead")) {
                org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/" + type, property.path("allOf").get(0).path("$ref").asText());
            } else org.junit.jupiter.api.Assertions.assertEquals(type, actualType, name + "." + field);
        });
    }

    private static void assertClosedEnum(tools.jackson.databind.JsonNode property, java.util.Set<String> expected) {
        java.util.Set<String> actual = new java.util.HashSet<>();
        property.path("enum").forEach(value -> actual.add(value.asText()));
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, "Generated enum must have exactly the approved vocabulary");
    }
}
