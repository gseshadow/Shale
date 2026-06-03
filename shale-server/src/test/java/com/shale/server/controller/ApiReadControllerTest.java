package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.server.runtime.ServerRuntimeSessionState;

class ApiReadControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiReadController apiReadController = new ApiReadController(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class),
                new ServerRuntimeSessionState());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(), apiReadController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedPort(Class<T> portType) {
        return (T) Proxy.newProxyInstance(
                portType.getClassLoader(),
                new Class<?>[] {portType},
                (proxy, method, args) -> {
                    throw new AssertionError("DB-backed service port should not be called before server session context exists: "
                            + method.getName());
                });
    }

    @Test
    void healthStillReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void caseSearchFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/search").param("query", "smith"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("not_implemented"))
                .andExpect(jsonPath("$.message", containsString("TODO: server auth/session context is not wired yet")))
                .andExpect(jsonPath("$.path").value("/api/cases/search"));
    }

    @Test
    void caseDetailRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/cases/123"));
    }

    @Test
    void caseTasksRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123/tasks"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/cases/123/tasks"));
    }

    @Test
    void contactSearchFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/contacts/search").param("query", "ada"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/contacts/search"));
    }

    @Test
    void unreadNotificationsFailClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/notifications/unread"));
    }
}
