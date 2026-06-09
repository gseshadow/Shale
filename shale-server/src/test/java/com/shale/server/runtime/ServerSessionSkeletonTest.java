package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ServerSessionSkeletonTest {

    @Test
    void unauthenticatedResolverReturnsUnavailableContext() {
        ServerSessionContext context = new UnauthenticatedServerSessionResolver().resolve(null);

        assertFalse(context.authenticated());
        assertTrue(context.principal().isEmpty());
    }


    @Test
    void developmentHeaderResolverBlocksMissingHeaders() {
        ServerSessionContext context = new DevelopmentHeaderServerSessionResolver()
                .resolve(new MockHttpServletRequest());

        assertFalse(context.authenticated());
        assertTrue(context.principal().isEmpty());
    }

    @Test
    void developmentHeaderResolverCreatesPrincipalFromValidHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "17");
        request.addHeader(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "23");

        ServerSessionContext context = new DevelopmentHeaderServerSessionResolver().resolve(request);

        assertTrue(context.authenticated());
        assertEquals(17, context.principal().orElseThrow().userId());
        assertEquals(23, context.principal().orElseThrow().shaleClientId());
    }

    @Test
    void runtimeSessionStateBlocksTenantAccessWhenResolverIsUnauthenticated() {
        ServerRuntimeSessionState state = new ServerRuntimeSessionState();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, state::requireShaleClientId);

        assertEquals(501, error.getStatusCode().value());
        assertTrue(error.getReason().contains("TODO: server auth/session context is not wired yet"));
    }

    @Test
    void principalCarriesUserAndTenantWithoutDefaults() {
        ServerPrincipal principal = new ServerPrincipal(7, 42, "user@example.com");
        ServerSessionContext context = ServerSessionContext.authenticated(principal);

        assertTrue(context.authenticated());
        assertEquals(7, context.principal().orElseThrow().userId());
        assertEquals(42, context.principal().orElseThrow().shaleClientId());
    }
}
