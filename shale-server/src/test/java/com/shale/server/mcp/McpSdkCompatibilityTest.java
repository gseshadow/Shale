package com.shale.server.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Prerelease compatibility probe only. No servlet registration, route, feature or Case consumer. */
class McpSdkCompatibilityTest {
    private static final String PROTOCOL = "2026-07-28";
    private static final String TEST_PATH = "/sdk-compatibility-unregistered";
    private static McpServer server() {
        return McpServer.builder().serverInfo(McpSchema.Implementation.builder("shale-sdk-probe", "test").build()).build();
    }
    private static JSONRPCRequest request(String version, String id) {
        return new JSONRPCRequest(McpSchema.METHOD_SERVER_DISCOVER, id, Map.of("_meta", Map.of(
                McpSchema.MetaKeys.PROTOCOL_VERSION, version,
                McpSchema.MetaKeys.CLIENT_CAPABILITIES, Map.of())));
    }

    @Test void jackson3ProviderAndModernSchemaRoundTripRejectMalformedJson() throws Exception {
        var mapper = assertInstanceOf(JacksonMcpJsonMapper.class, McpJsonDefaults.getMapper());
        assertEquals("3.1.5", mapper.getJsonMapper().version().toString(), "Boot's application Jackson must be retained");
        assertEquals("3.1.5", JsonMapper.builder().build().version().toString());
        var original = request(PROTOCOL, "roundtrip");
        assertEquals(original, JsonRpc.deserializeMessage(mapper, mapper.writeValueAsString(original)));
        var schema = McpSchema.DiscoverResult.builder(List.of(PROTOCOL), McpSchema.ServerCapabilities.builder().build()).build();
        var copy = mapper.readValue(mapper.writeValueAsString(schema), McpSchema.DiscoverResult.class);
        assertEquals(schema, copy, "Modern schema annotations must work with Jackson 3.1.5");
        assertThrows(IOException.class, () -> JsonRpc.deserializeMessage(mapper, "{"));
        assertThrows(JsonRpc.InvalidMessageException.class, () -> JsonRpc.deserializeMessage(mapper, "[]"));
        var validator = McpJsonDefaults.getSchemaValidator();
        var objectSchema = Map.<String,Object>of("type", "object", "required", List.of("value"),
                "properties", Map.of("value", Map.of("type", "string")));
        assertTrue(validator.validateSchema(objectSchema).valid(), "NetworkNT provider must initialize");
        assertTrue(validator.validate(objectSchema, Map.of("value", "synthetic")).valid());
        assertFalse(validator.validate(objectSchema, Map.of("value", 42)).valid());
    }

    @Test void modernDefaultServerNegotiatesExactlyPinnedProtocolWithoutSessionOrInitialization() {
        assertEquals(PROTOCOL, McpSchema.LATEST_PROTOCOL_VERSION);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(McpServer.class, McpSdkCompatibilityTest::server);
            context.refresh();
            var server = context.getBean(McpServer.class);
            try {
                for (String id : List.of("independent-one", "independent-two")) {
                    var result = assertInstanceOf(McpTransportResponse.Result.class,
                            server.handleBlocking(McpTransportContext.EMPTY, request(PROTOCOL, id)).block(Duration.ofSeconds(5)));
                    assertEquals(id, result.response().id());
                    assertNull(result.response().error());
                    var discovery = McpJsonDefaults.getMapper().convertValue(result.response().result(), McpSchema.DiscoverResult.class);
                    assertEquals(List.of(PROTOCOL), discovery.supportedVersions(), "Default server must advertise the actual protocol");
                }
                var rejected = assertInstanceOf(McpTransportResponse.Error.class,
                        server.handle(McpTransportContext.EMPTY, request("2025-11-25", "legacy")).block(Duration.ofSeconds(5)));
                assertEquals(McpSchema.ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, rejected.response().error().code());
                var data = McpJsonDefaults.getMapper().convertValue(rejected.response().error().data(),
                        McpSchema.UnsupportedProtocolVersionData.class);
                assertEquals(List.of(PROTOCOL), data.supported());
            } finally { server.closeGracefully(); }
        }
    }

    @Test void unregisteredModernServletInitializesOnCurrentSpringTomcatAndNeedsNoSessionOrGetStream() throws Exception {
        assertEquals("7.0.9", org.springframework.core.SpringVersion.getVersion());
        assertEquals("7.1.1", org.springframework.security.core.SpringSecurityCoreVersion.getVersion());
        assertEquals("4.1.1", org.springframework.boot.SpringBootVersion.getVersion());
        assertEquals("Apache Tomcat/11.0.24", org.apache.catalina.util.ServerInfo.getServerInfo());
        var tomcatContext = new org.apache.catalina.core.StandardContext();
        assertEquals(6, tomcatContext.getServletContext().getMajorVersion());
        assertEquals(1, tomcatContext.getServletContext().getMinorVersion());
        var server = server();
        var servlet = HttpServletMcpTransport.builder(server).endpoint(TEST_PATH).build();
        servlet.init(new MockServletConfig());
        try {
            for (boolean straySessionHeader : List.of(false, true)) {
                var request = new MockHttpServletRequest("POST", TEST_PATH);
                request.setContentType("application/json");
                request.addHeader("Accept", "application/json");
                request.addHeader("Mcp-Method", McpSchema.METHOD_SERVER_DISCOVER);
                request.addHeader("MCP-Protocol-Version", PROTOCOL);
                if (straySessionHeader) request.addHeader("Mcp-Session-Id", "ignored-synthetic");
                request.setContent(McpJsonDefaults.getMapper().writeValueAsBytes(request(PROTOCOL, "post")));
                var response = new MockHttpServletResponse();
                servlet.service(request, response);
                assertEquals(200, response.getStatus(), response.getContentAsString());
                assertTrue(response.getContentType().startsWith("application/json"));
                assertNull(response.getHeader("Mcp-Session-Id"), "Modern path must never mint a protocol session");
                assertNull(request.getSession(false), "No HTTP session may be required");
                assertFalse(request.isAsyncStarted(), "JSON response must not require an SSE stream");
                var parsed = McpJsonDefaults.getMapper().readValue(response.getContentAsString(), JSONRPCResponse.class);
                assertNull(parsed.error());
            }
            for (String method : List.of("GET", "DELETE")) {
                var response = new MockHttpServletResponse();
                servlet.service(new MockHttpServletRequest(method, TEST_PATH), response);
                assertEquals(405, response.getStatus(), "Legacy session/GET stream must not be required");
                assertNull(response.getHeader("Mcp-Session-Id"));
            }
            var malformed = new MockHttpServletRequest("POST", TEST_PATH);
            malformed.setContent("{".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            servlet.service(malformed, response);
            assertEquals(400, response.getStatus());
            assertEquals(McpSchema.ErrorCodes.PARSE_ERROR,
                    McpJsonDefaults.getMapper().readValue(response.getContentAsString(), JSONRPCResponse.class).error().code());
        } finally { servlet.destroy(); }
    }
}
