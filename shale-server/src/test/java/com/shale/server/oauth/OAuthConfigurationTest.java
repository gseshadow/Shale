package com.shale.server.oauth;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.shale.server.oauth.McpOAuthProperties.Identity.*;

class OAuthConfigurationTest {
    @Test void canonicalIdentityHasOneExactResourceAndNoAliases() {
        var properties = new McpOAuthProperties("https://shale.example", PRODUCTION, true);
        assertEquals("https://shale.example/oauth/mcp", properties.issuer());
        assertEquals("https://shale.example/mcp", properties.resource());
        properties.validateResources(List.of(properties.resource()));
        for (var values : List.of(List.<String>of(), List.of(properties.resource(), properties.resource()),
                List.of(properties.resource(), "https://other.example/mcp"), List.of(properties.resource() + "/"),
                List.of("https://shale.example/api"), List.of("https://attacker.example/mcp")))
            assertThrows(IllegalArgumentException.class, () -> properties.validateResources(values), values.toString());
        assertThrows(IllegalArgumentException.class, () -> properties.validateResources(null));
        assertEquals(Map.of("resource", properties.resource(), "authorization_servers", List.of(properties.issuer())),
                OAuthMetadataContract.protectedResource(properties));
    }

    @Test void unsafeOrNoncanonicalProductionConfigurationFailsEvenWhenCompositionOff() {
        for (String origin : List.of("", "garbage", "http://shale.example", "https://shale.example/", "https://shale.example/oauth/mcp",
                "https://USER@shale.example", "https://shale.example?x=1", "https://shale.example#x", "https://SHALE.example",
                "https://shale.example:443", "https://shale.example:0", "https://shale.example:65536", "https://shale.example.",
                "https://localhost", "https://127.0.0.1", "https://[::1]", "https://shale.test", "https://shale.invalid",
                "https://shale.example%2f.evil", "https://shale.example\\evil", " https://shale.example"))
            assertThrows(IllegalArgumentException.class, () -> new McpOAuthProperties(origin, PRODUCTION, false), origin);
    }

    @Test void defaultsHaveNoIdentityOrOperationalCompositionAndDevCannotReuseProduction() {
        var defaults = new McpOAuthProperties(null, null, false);
        assertEquals(PRODUCTION, defaults.identity());
        assertFalse(defaults.compositionEnabled());
        assertThrows(IllegalStateException.class, defaults::issuer);
        assertThrows(IllegalArgumentException.class, () -> new McpOAuthProperties(null, PRODUCTION, true));
        for (var identity : List.of(DEVELOPMENT, TEST)) {
            assertThrows(IllegalArgumentException.class, () -> new McpOAuthProperties("https://shale.example", identity, true));
            assertEquals("http://127.0.0.1:9999/mcp", new McpOAuthProperties("http://127.0.0.1:9999", identity, true).resource());
            assertEquals("https://shale.test/oauth/mcp", new McpOAuthProperties("https://shale.test", identity, true).issuer());
        }
    }

    @Test void noCredentialOrConsentFallbackCanPersistOrResolveAuthority() {
        var repository = new OAuthIssuanceDisabled();
        assertNull(repository.findByClientId("synthetic"));
        assertNull(repository.findById("synthetic"));
        assertThrows(UnsupportedOperationException.class, () -> repository.save(null));
        assertNull(OAuthIssuanceDisabled.AUTHORIZATIONS.findById("synthetic"));
        assertNull(OAuthIssuanceDisabled.AUTHORIZATIONS.findByToken("ordinary-jwt", null));
        assertThrows(UnsupportedOperationException.class, () -> OAuthIssuanceDisabled.AUTHORIZATIONS.save(null));
        assertThrows(UnsupportedOperationException.class, () -> OAuthIssuanceDisabled.AUTHORIZATIONS.remove(null));
        assertNull(OAuthIssuanceDisabled.CONSENTS.findById("synthetic", "web-user"));
        assertThrows(UnsupportedOperationException.class, () -> OAuthIssuanceDisabled.CONSENTS.save(null));
        assertThrows(UnsupportedOperationException.class, () -> OAuthIssuanceDisabled.CONSENTS.remove(null));
        assertNull(OAuthIssuanceDisabled.TOKENS.generate(null));
    }
}
