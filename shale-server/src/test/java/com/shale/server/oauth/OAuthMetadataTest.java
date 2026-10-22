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

    @Test void authorizationOnlyMetadataDoesNotPromiseTokenExchange() {
        var properties=new McpOAuthProperties("https://shale.example",McpOAuthProperties.Identity.PRODUCTION,true);
        var claims=OAuthMetadataContract.authorizationServer(properties,true);
        assertEquals(properties.issuer()+"/authorize",claims.get("authorization_endpoint"));
        assertEquals(List.of("code"),claims.get("response_types_supported"));
        assertEquals(List.of("S256"),claims.get("code_challenge_methods_supported"));
        assertEquals(true,claims.get("authorization_response_iss_parameter_supported"));
        assertEquals(List.of(),claims.get("grant_types_supported"),"HTTP token exchange remains unavailable in 3B.2B");
        assertEquals(List.of(),claims.get("token_endpoint_auth_methods_supported"));
        assertFalse(claims.containsKey("token_endpoint"));
        assertEquals(OAuthMetadataContract.authorizationServer(properties),OAuthMetadataContract.authorizationServer(properties,false));
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
    @Test void enabledIssuanceAdvertisesOnlyCodeRefreshNoneAndPinnedJwt() {
        var p=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,true);
        var claims=OAuthMetadataContract.authorizationServer(p,true,true);
        assertEquals(java.util.List.of("authorization_code","refresh_token"),claims.get("grant_types_supported"));
        assertEquals(java.util.List.of("none","private_key_jwt"),claims.get("token_endpoint_auth_methods_supported"));
        assertEquals(java.util.List.of("RS256"),claims.get("token_endpoint_auth_signing_alg_values_supported"));
        assertEquals(p.issuer()+"/token",claims.get("token_endpoint"));
        assertEquals(p.issuer()+"/revoke",claims.get("revocation_endpoint"));
        assertEquals(List.of("none","private_key_jwt"),claims.get("revocation_endpoint_auth_methods_supported"));
        assertEquals(List.of("RS256"),claims.get("revocation_endpoint_auth_signing_alg_values_supported"));
        for(String k:java.util.List.of("jwks_uri","userinfo_endpoint","registration_endpoint"))assertFalse(claims.containsKey(k));
    }

}
