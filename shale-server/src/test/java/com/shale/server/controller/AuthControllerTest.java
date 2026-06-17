package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;
import com.shale.server.auth.CurrentUserProfileService;
import com.shale.server.dto.AuthenticatedUserResponse;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.InMemoryTokenRevocationStore;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ShaleAuthTokenService;
import com.shale.server.runtime.TokenRevocationStore;

import jakarta.servlet.http.HttpServletRequest;

class AuthControllerTest {
    private static final String TEST_SECRET = "test-auth-token-secret-that-is-long-enough";
    private RecordingAuthServicePort authServicePort;
    private ShaleAuthTokenService tokenService;
    private TokenRevocationStore revocationStore;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authServicePort = new RecordingAuthServicePort();
        tokenService = new ShaleAuthTokenService(TEST_SECRET, 3600, java.time.Clock.systemUTC());
        revocationStore = new InMemoryTokenRevocationStore();
        CurrentUserProfileService profileService = principal -> java.util.Optional.of(new AuthenticatedUserResponse(
                true,
                principal.userId(),
                principal.shaleClientId(),
                principal.email(),
                "Ada Lovelace",
                "Ada",
                "Lovelace",
                true,
                false,
                "AL",
                "#123456"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        authServicePort,
                        tokenService,
                        revocationStore,
                        new ServerRuntimeSessionState(new BearerTokenServerSessionResolver(tokenService, revocationStore), currentRequestProvider()),
                        profileService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginAuthenticatesCredentialsAndReturnsBearerTokenAndSafeUserPayload() throws Exception {
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
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.user.authenticated").value(true))
                .andExpect(jsonPath("$.user.userId").value(42))
                .andExpect(jsonPath("$.user.shaleClientId").value(7))
                .andExpect(jsonPath("$.user.email").value("ada@example.test"))
                .andExpect(jsonPath("$.user.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.user.nameFirst").value("Ada"))
                .andExpect(jsonPath("$.user.nameLast").value("Lovelace"))
                .andExpect(content().string(not(containsString("correct horse battery staple"))))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertEquals("ada@example.test", authServicePort.email);
        org.junit.jupiter.api.Assertions.assertEquals("correct horse battery staple", authServicePort.password);
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        org.junit.jupiter.api.Assertions.assertTrue(tokenService.verifyToken(accessToken).orElseThrow().tokenId().length() > 10);
        org.junit.jupiter.api.Assertions.assertNull(result.getResponse().getCookie("JSESSIONID"));
    }

    @Test
    void loginReturnsSafeUnauthorizedResponseOnBadPassword() throws Exception {
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
    }

    @Test
    void loginReturnsSafeUnauthorizedResponseOnUnknownUser() throws Exception {
        authServicePort.nextResult = Result.fail("Invalid credentials.");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.error").value("invalid_credentials"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."))
                .andExpect(content().string(not(containsString("unknown"))));
    }

    @Test
    void meReturnsCurrentPrincipalWithValidBearerToken() throws Exception {
        String token = tokenService.issue(new com.shale.server.runtime.ServerPrincipal(42, 7, "ada@example.test"));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.shaleClientId").value(7))
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.nameFirst").value("Ada"))
                .andExpect(jsonPath("$.nameLast").value("Lovelace"))
                .andExpect(jsonPath("$.isAdmin").value(true))
                .andExpect(jsonPath("$.isAttorney").value(false))
                .andExpect(jsonPath("$.initials").value("AL"))
                .andExpect(jsonPath("$.color").value("#123456"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("hash"))));
    }


    @Test
    void logoutRevokesCurrentTokenAndMeRejectsRevokedToken() throws Exception {
        String token = tokenService.issue(new com.shale.server.runtime.ServerPrincipal(42, 7, "ada@example.test"));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void logoutWithMissingOrInvalidTokenReturnsSafeResponse() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.message").value("Logged out."));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.message").value("Logged out."));
    }

    @Test
    void meFailsClosedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meFailsClosedWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
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
                .andExpect(jsonPath("$.user.displayName").value("Prince"))
                .andExpect(jsonPath("$.user.nameFirst").value("Prince"))
                .andExpect(jsonPath("$.user.nameLast").doesNotExist());
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
