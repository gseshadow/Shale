package com.shale.server.oauth;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadata;
import static org.junit.jupiter.api.Assertions.*;

class OAuthMetadataTest {
    @Test void foundationAvoidsAdvertisingOperationalBuilderDefaults() {
        var properties = new McpOAuthProperties("https://shale.example", McpOAuthProperties.Identity.PRODUCTION, true);
        var missingEndpoint = assertThrows(IllegalArgumentException.class,
                () -> OAuth2AuthorizationServerMetadata.builder().issuer(properties.issuer()).build());
        assertTrue(missingEndpoint.getMessage().contains("authorizationEndpoint"), "Re-evaluate metadata withholding when library requirements change");
        assertThrows(IllegalArgumentException.class, () -> OAuth2AuthorizationServerMetadata.builder()
                .issuer(properties.issuer()).authorizationEndpoint(properties.issuer() + "/authorize")
                .tokenEndpoint(properties.issuer() + "/token").responseType("code")
                .grantTypes(List::clear).build(), "Empty grants must not silently become framework defaults");
        assertEquals(Map.of("issuer", properties.issuer(), "response_types_supported", List.of(),
                "grant_types_supported", List.of(), "token_endpoint_auth_methods_supported", List.of()),
                OAuthMetadataContract.authorizationServer(properties));
        assertEquals(Map.of("resource", "https://shale.example/mcp",
                "authorization_servers", List.of("https://shale.example/oauth/mcp")),
                OAuthMetadataContract.protectedResource(properties));
    }

    @Test void bootBindingFailsClosedForInvalidOriginOrCrossProfileIdentity() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(McpOAuthSecurityConfiguration.class);
        for (String origin : List.of("http://shale.example", "https://shale.example/", "https://shale.example?host=evil"))
            runner.withPropertyValues("shale.ai.oauth.public-origin=" + origin).run(context ->
                    assertNotNull(context.getStartupFailure(), "Invalid configured production origin must fail startup even with composition disabled"));
        runner.withPropertyValues("shale.ai.oauth.composition-enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("shale.ai.oauth.public-origin=https://shale.test", "shale.ai.oauth.identity=TEST")
                .run(context -> assertNotNull(context.getStartupFailure(), "A test identity cannot run as the default production identity"));
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "test"))
                .withPropertyValues("shale.ai.oauth.public-origin=https://shale.test", "shale.ai.oauth.identity=TEST")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("shale.ai.oauth.public-origin=https://shale.example")
                .run(context -> assertNotNull(context.getStartupFailure(), "Development must not reuse the production issuer"));
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.getBean(McpOAuthProperties.class).compositionEnabled());
        });
    }
}
