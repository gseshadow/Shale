package com.shale.server.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.shale.server.runtime.*;

class SessionControllerTest {
    @Test void selfListDerivesUserTenantAndCurrentSessionOnlyFromAuthenticatedServerState() throws Exception {
        var fixture=fixture();
        when(fixture.service.listOwnSessions(fixture.principal,fixture.currentSid)).thenReturn(List.of());

        fixture.mvc.perform(get("/api/sessions").header("Authorization","Bearer "+fixture.bearer))
                .andExpect(status().isOk());

        verify(fixture.service).listOwnSessions(fixture.principal,fixture.currentSid);
        verifyNoMoreInteractions(fixture.service);
    }

    @Test void ownedRevokeBindsTheRequestedSessionButRetainsAuthenticatedOwnerAuthority() throws Exception {
        var fixture=fixture();var target=UUID.randomUUID();

        fixture.mvc.perform(post("/api/sessions/{sessionId}/revoke",target)
                        .header("Authorization","Bearer "+fixture.bearer))
                .andExpect(status().isNoContent());

        verify(fixture.service).revokeOwnSession(fixture.principal,fixture.currentSid,target);
    }

    @Test void currentRevokeUsesBoundSessionIdentityRatherThanAClientSuppliedId() throws Exception {
        var fixture=fixture();

        fixture.mvc.perform(post("/api/sessions/current/revoke")
                        .header("Authorization","Bearer "+fixture.bearer))
                .andExpect(status().isNoContent());

        verify(fixture.service).revokeCurrentSession(fixture.principal,fixture.currentSid);
    }

    private static Fixture fixture(){
        var service=mock(SessionManagementService.class);var state=mock(ServerRuntimeSessionState.class);
        var principal=new ServerPrincipal(41,7,"user@example.test");when(state.requirePrincipal()).thenReturn(principal);
        var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());
        var sid=UUID.randomUUID();var bearer=tokens.issueBound(tokens.prepare(principal),sid);
        var mvc=MockMvcBuilders.standaloneSetup(new SessionController(service,state,tokens)).setControllerAdvice(new ApiExceptionHandler()).build();
        return new Fixture(service,principal,sid,bearer,mvc);
    }

    private record Fixture(SessionManagementService service,ServerPrincipal principal,UUID currentSid,String bearer,org.springframework.test.web.servlet.MockMvc mvc) {}
}
