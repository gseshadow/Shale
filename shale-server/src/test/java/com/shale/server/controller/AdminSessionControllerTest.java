package com.shale.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.model.ClientType;
import com.shale.server.dto.UserSessionResponse;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.SessionManagementService;
import com.shale.server.runtime.ShaleAuthTokenService;

class AdminSessionControllerTest {
    @Test void boundedFiltersAndBoundSessionReachAdminServiceAndReturnExpectedPageDto() throws Exception {
        var service=mock(SessionManagementService.class);
        var state=mock(ServerRuntimeSessionState.class);
        var principal=new ServerPrincipal(9,7,"admin@example.test");
        when(state.requirePrincipal()).thenReturn(principal);
        var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());
        var prepared=tokens.prepare(principal);var sid=UUID.randomUUID();String bearer=tokens.issueBound(prepared,sid);
        var row=new UserSessionResponse(sid,9,"Admin User","admin@example.test",ClientType.DESKTOP,Instant.parse("2026-10-02T10:00:00Z"),Instant.parse("2026-10-02T11:00:00Z"),null,null,null,true);
        when(service.listTenantSessionsAsAdmin(eq(principal),eq(sid),eq(12),eq(ClientType.DESKTOP),eq(true),eq(Instant.parse("2026-09-01T00:00:00Z")),eq(2),eq(50))).thenReturn(List.of(row));
        var mvc=MockMvcBuilders.standaloneSetup(new AdminSessionController(service,state,tokens)).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/api/admin/sessions").header("Authorization","Bearer "+bearer).param("page","2").param("size","50").param("userId","12").param("clientType","DESKTOP").param("activeOnly","true").param("since","2026-09-01T00:00:00Z"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.size").value(50))
            .andExpect(jsonPath("$.items[0].sessionId").value(sid.toString())).andExpect(jsonPath("$.items[0].clientType").value("DESKTOP"))
            .andExpect(jsonPath("$.items[0].currentSession").value(true));
        verify(service).listTenantSessionsAsAdmin(principal,sid,12,ClientType.DESKTOP,true,Instant.parse("2026-09-01T00:00:00Z"),2,50);
    }
}
