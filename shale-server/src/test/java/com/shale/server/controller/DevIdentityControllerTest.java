package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.ServerRuntimeSessionState;

import jakarta.servlet.http.HttpServletRequest;

class DevIdentityControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ServerRuntimeSessionState runtimeSessionState = new ServerRuntimeSessionState(
                new DevelopmentHeaderServerSessionResolver(),
                currentRequestProvider());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DevIdentityController(runtimeSessionState))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void whoamiBlocksMissingDevelopmentHeaders() throws Exception {
        mockMvc.perform(get("/api/dev/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication is required")));
    }

    @Test
    void whoamiReturnsResolvedDevelopmentPrincipal() throws Exception {
        mockMvc.perform(get("/api/dev/whoami")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "53")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(53))
                .andExpect(jsonPath("$.shaleClientId").value(59));
    }

    private static ObjectProvider<HttpServletRequest> currentRequestProvider() {
        return new ObjectProvider<>() {
            @Override
            public HttpServletRequest getObject(Object... args) {
                return getIfAvailable();
            }

            @Override
            public HttpServletRequest getIfAvailable() {
                var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttributes) {
                    return servletAttributes.getRequest();
                }
                return null;
            }

            @Override
            public HttpServletRequest getIfUnique() {
                return getIfAvailable();
            }

            @Override
            public HttpServletRequest getObject() {
                return getIfAvailable();
            }
        };
    }
}
