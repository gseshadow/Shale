package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;

class AuthControllerTest {
    private RecordingAuthServicePort authServicePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authServicePort = new RecordingAuthServicePort();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authServicePort))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginAuthenticatesCredentialsAndReturnsTemporarySuccessShape() throws Exception {
        authServicePort.nextResult = Result.ok(User.builder()
                .id(42)
                .shaleClientId(7)
                .nameFirst("Ada")
                .nameLast("Lovelace")
                .email("ada@example.test")
                .build());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ada@example.test\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.shaleClientId").value(7))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.nameFirst").value("Ada"))
                .andExpect(jsonPath("$.nameLast").value("Lovelace"))
                .andExpect(jsonPath("$.todo", containsString("token/session issuance is not implemented yet")))
                .andExpect(content().string(not(containsString("correct horse battery staple"))))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertEquals("ada@example.test", authServicePort.email);
        org.junit.jupiter.api.Assertions.assertEquals("correct horse battery staple", authServicePort.password);
        org.junit.jupiter.api.Assertions.assertNull(result.getResponse().getCookie("JSESSIONID"));
    }

    @Test
    void loginReturnsSafeUnauthorizedResponseOnAuthenticationFailure() throws Exception {
        authServicePort.nextResult = Result.fail("database-specific auth failure should not leak");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ada@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.error").value("invalid_credentials"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."))
                .andExpect(content().string(not(containsString("wrong"))))
                .andExpect(content().string(not(containsString("database-specific"))));

        org.junit.jupiter.api.Assertions.assertEquals("ada@example.test", authServicePort.email);
        org.junit.jupiter.api.Assertions.assertEquals("wrong", authServicePort.password);
    }

    @Test
    void loginUsesAvailableSingleNameForDisplayName() throws Exception {
        authServicePort.nextResult = Result.ok(User.builder()
                .id(5)
                .shaleClientId(9)
                .nameFirst("Prince")
                .build());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"prince@example.test\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Prince"))
                .andExpect(jsonPath("$.nameFirst").value("Prince"))
                .andExpect(jsonPath("$.nameLast").doesNotExist());
    }

    private static final class RecordingAuthServicePort implements AuthServicePort {
        private Result<User> nextResult = Result.fail("not configured");
        private String email;
        private String password;

        @Override
        public Result<User> authenticate(String email, String password) {
            this.email = email;
            this.password = password;
            return nextResult;
        }
    }
}
