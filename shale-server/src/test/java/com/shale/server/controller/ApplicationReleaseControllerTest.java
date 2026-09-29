package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.dto.ApplicationPolicyView;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.dto.ApplicationReleaseItemView;
import com.shale.core.dto.ApplicationReleaseView;
import com.shale.core.model.ApplicationAccessMode;
import com.shale.core.model.PublicationStatus;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.ReleaseItemType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.InMemoryTokenRevocationStore;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ShaleAuthTokenService;

import jakarta.servlet.http.HttpServletRequest;

class ApplicationReleaseControllerTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-28T12:00:00Z");
    private RecordingReleaseService service;
    private ShaleAuthTokenService tokenService;
    private MockMvc mockMvc;
    private String bearerToken;

    @BeforeEach
    void setUp() {
        service = new RecordingReleaseService();
        tokenService = new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough", 3600,
                java.time.Clock.systemUTC());
        var resolver = new BearerTokenServerSessionResolver(tokenService, new InMemoryTokenRevocationStore());
        mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationReleaseController(service,
                        new ServerRuntimeSessionState(resolver, currentRequestProvider())))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        bearerToken = "Bearer " + tokenService.issue(new ServerPrincipal(31, 41, "ada@example.test"));
    }

    @Test
    void unauthenticatedRequestsAreRejectedWithoutReadingGlobalData() throws Exception {
        mockMvc.perform(get("/api/application-releases/policy/current").param("channel", "PRODUCTION"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        org.junit.jupiter.api.Assertions.assertNull(service.policyChannel,
                "Unauthenticated callers must not reach the release read service");
    }

    @Test
    void currentPolicyUsesCanonicalVersionsNullableReferencesAndPrivateBoundedCaching() throws Exception {
        service.policy = Optional.of(new ApplicationPolicyView(9, ReleaseChannel.PRODUCTION, 4,
                new ReleaseReference(130, SemanticVersion.parse("1.0.130")), null,
                new ReleaseReference(99, SemanticVersion.parse("1.0.99")), null,
                ApplicationAccessMode.READ_ONLY, PUBLISHED_AT, PUBLISHED_AT, new byte[] {1}));

        mockMvc.perform(authenticated(get("/api/application-releases/policy/current")
                        .param("channel", "PRODUCTION")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, private"))
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.channel").value("PRODUCTION"))
                .andExpect(jsonPath("$.revisionNumber").value(4))
                .andExpect(jsonPath("$.latestVersion").value("1.0.130"))
                .andExpect(jsonPath("$.latestReleaseId").value(130))
                .andExpect(jsonPath("$.minimumRecommendedVersion").doesNotExist())
                .andExpect(jsonPath("$.minimumRecommendedReleaseId").doesNotExist())
                .andExpect(jsonPath("$.minimumAllowedVersion").value("1.0.99"))
                .andExpect(jsonPath("$.accessMode").value("READ_ONLY"))
                .andExpect(jsonPath("$.publishedAt").value("2026-09-28T12:00:00Z"))
                .andExpect(jsonPath("$.serverTime").value("2026-09-28T12:00:00Z"))
                .andExpect(content().string(not(containsString("rowVersion"))))
                .andExpect(content().string(not(containsString("actor"))));
    }

    @Test
    void absentCurrentPolicyReturnsDocumentedNoContentResponse() throws Exception {
        mockMvc.perform(authenticated(get("/api/application-releases/policy/current")
                        .param("channel", "PILOT")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "max-age=60, private"))
                .andExpect(content().string(""));
    }

    @Test
    void releasesUseExclusiveBoundNumericOrderCorrectChannelAndOrderedActiveItems() throws Exception {
        service.releases = List.of(
                release(2, "1.0.100", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED),
                release(3, "1.0.130", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED));
        service.items = List.of(
                new ApplicationReleaseItemView(11, 2, 0, ReleaseItemType.FEATURE, "Feature", "Body", null, true),
                new ApplicationReleaseItemView(12, 2, 1, ReleaseItemType.LINK, "Docs", "Read", "https://example.test", true));

        mockMvc.perform(authenticated(get("/api/application-releases")
                        .param("channel", "PRODUCTION").param("after", "1.0.99")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, private"))
                .andExpect(jsonPath("$[0].version").value("1.0.100"))
                .andExpect(jsonPath("$[1].version").value("1.0.130"))
                .andExpect(jsonPath("$[0].channel").value("PRODUCTION"))
                .andExpect(jsonPath("$[0].items[0].sortOrder").value(0))
                .andExpect(jsonPath("$[0].items[0].type").value("FEATURE"))
                .andExpect(jsonPath("$[0].items[0].resourceUrl").doesNotExist())
                .andExpect(jsonPath("$[0].items[1].type").value("LINK"))
                .andExpect(jsonPath("$[0].items[1].resourceUrl").value("https://example.test"))
                .andExpect(content().string(not(containsString("publicationStatus"))))
                .andExpect(content().string(not(containsString("rowVersion"))));

        org.junit.jupiter.api.Assertions.assertEquals(SemanticVersion.parse("1.0.99"), service.lowerBound,
                "The endpoint must pass the exact exclusive semantic lower bound to Phase 2A");
        org.junit.jupiter.api.Assertions.assertEquals(ReleaseChannel.PRODUCTION, service.releaseChannel,
                "The endpoint must pass the selected channel without tenant substitution");
    }

    @Test
    void draftAndWrongChannelRowsFromTheReadBoundaryFailClosedWithoutExposingItems() throws Exception {
        service.releases = List.of(release(7, "1.0.130", ReleaseChannel.PILOT, PublicationStatus.DRAFT));

        mockMvc.perform(authenticated(get("/api/application-releases")
                        .param("channel", "PRODUCTION").param("after", "1.0.99").param("status", "DRAFT")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(content().string(not(containsString("DRAFT"))))
                .andExpect(content().string(not(containsString("database"))));

        org.junit.jupiter.api.Assertions.assertTrue(service.requestedItemReleaseIds.isEmpty(),
                "Items belonging to an invalid/draft release must never be read or exposed");
    }

    @Test
    void malformedVersionAndInvalidChannelUseStandardSafeValidationEnvelope() throws Exception {
        mockMvc.perform(authenticated(get("/api/application-releases")
                        .param("channel", "PRODUCTION").param("after", "v1.0.127")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Version must use strict canonical major.minor.build format."));

        mockMvc.perform(authenticated(get("/api/application-releases/policy/current")
                        .param("channel", "production")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid release channel."));
    }

    @Test
    void invariantAndUnexpectedFailuresUseSafeInternalErrorEnvelope() throws Exception {
        service.failure = new IllegalStateException("SELECT * FROM dbo.ApplicationPolicy\nstack trace secret");

        mockMvc.perform(authenticated(get("/api/application-releases/policy/current")
                        .param("channel", "PRODUCTION")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("dbo.ApplicationPolicy"))))
                .andExpect(content().string(not(containsString("stack trace"))));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearerToken);
    }

    private static ApplicationReleaseView release(long id, String version, ReleaseChannel channel,
            PublicationStatus status) {
        return new ApplicationReleaseView(id, SemanticVersion.parse(version), channel, status, PUBLISHED_AT,
                "Summary", new byte[] {1});
    }

    private static ObjectProvider<HttpServletRequest> currentRequestProvider() {
        return new ObjectProvider<>() {
            @Override public HttpServletRequest getObject(Object... args) { return getIfAvailable(); }
            @Override public HttpServletRequest getIfAvailable() {
                var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                return attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                        ? servlet.getRequest() : null;
            }
            @Override public HttpServletRequest getIfUnique() { return getIfAvailable(); }
            @Override public HttpServletRequest getObject() { return getIfAvailable(); }
        };
    }

    private static final class RecordingReleaseService implements ApplicationReleaseReadServicePort {
        Optional<ApplicationPolicyView> policy = Optional.empty();
        List<ApplicationReleaseView> releases = List.of();
        List<ApplicationReleaseItemView> items = List.of();
        final java.util.ArrayList<Long> requestedItemReleaseIds = new java.util.ArrayList<>();
        ReleaseChannel policyChannel;
        ReleaseChannel releaseChannel;
        SemanticVersion lowerBound;
        RuntimeException failure;

        @Override public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel channel) {
            policyChannel = channel;
            if (failure != null) throw failure;
            return policy;
        }
        @Override public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel channel,
                SemanticVersion exclusiveLowerBound) {
            releaseChannel = channel;
            lowerBound = exclusiveLowerBound;
            if (failure != null) throw failure;
            return releases;
        }
        @Override public List<ApplicationReleaseItemView> listReleaseItems(long releaseId) {
            requestedItemReleaseIds.add(releaseId);
            return items.stream().filter(item -> item.releaseId() == releaseId).toList();
        }
    }
}
