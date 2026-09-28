package com.shale.server.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.dto.ApplicationPolicyView;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.dto.ApplicationReleaseItemView;
import com.shale.core.dto.ApplicationReleaseView;
import com.shale.core.model.PublicationStatus;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.server.dto.ApiErrorResponse;
import com.shale.server.dto.ApplicationPolicyResponse;
import com.shale.server.dto.ApplicationReleaseItemResponse;
import com.shale.server.dto.ApplicationReleaseResponse;
import com.shale.server.runtime.ServerRuntimeSessionState;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/application-releases")
@Tag(name = "Application Releases", description = "Authenticated, global read-only release and policy metadata")
@SecurityRequirement(name = "bearerAuth")
public final class ApplicationReleaseController {
    private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate();

    private final ApplicationReleaseReadServicePort releaseReadService;
    private final ServerRuntimeSessionState runtimeSessionState;

    public ApplicationReleaseController(ApplicationReleaseReadServicePort releaseReadService,
            ServerRuntimeSessionState runtimeSessionState) {
        this.releaseReadService = releaseReadService;
        this.runtimeSessionState = runtimeSessionState;
    }

    @Operation(summary = "Get current application policy",
            description = "Returns the current effective global policy for the requested channel. Returns 204 when no policy is configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current effective policy",
                    content = @Content(schema = @Schema(implementation = ApplicationPolicyResponse.class))),
            @ApiResponse(responseCode = "204", description = "No current policy is configured", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid channel", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Safe internal error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/policy/current")
    public ResponseEntity<ApplicationPolicyResponse> currentPolicy(
            @Parameter(description = "Release channel", required = true,
                    schema = @Schema(allowableValues = {"PRODUCTION", "PILOT", "DEVELOPMENT"}))
            @RequestParam String channel) {
        requireAuthenticatedCaller();
        ReleaseChannel parsedChannel = parseChannel(channel);
        return releaseReadService.findCurrentPolicy(parsedChannel)
                .map(policy -> ResponseEntity.ok().cacheControl(CACHE_CONTROL).body(toResponse(policy)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CACHE_CONTROL).build());
    }

    @Operation(summary = "List published application releases since a version",
            description = "Returns only published releases in ascending numeric semantic-version order, strictly greater than the exclusive lower bound. Active release items are embedded in deterministic order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Published releases after the supplied version",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApplicationReleaseResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid channel or non-canonical version", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Safe internal error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ApplicationReleaseResponse>> publishedReleases(
            @Parameter(description = "Release channel", required = true,
                    schema = @Schema(allowableValues = {"PRODUCTION", "PILOT", "DEVELOPMENT"}))
            @RequestParam String channel,
            @Parameter(description = "Exclusive lower bound in strict canonical major.minor.build form", required = true,
                    example = "1.0.127", schema = @Schema(pattern = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$"))
            @RequestParam String after) {
        requireAuthenticatedCaller();
        ReleaseChannel parsedChannel = parseChannel(channel);
        SemanticVersion lowerBound = parseVersion(after);
        List<ApplicationReleaseResponse> releases = releaseReadService
                .listPublishedReleasesAfter(parsedChannel, lowerBound).stream()
                .map(release -> toResponse(release, parsedChannel, lowerBound))
                .toList();
        return ResponseEntity.ok().cacheControl(CACHE_CONTROL).body(releases);
    }

    private void requireAuthenticatedCaller() {
        // Resolve both established identity dimensions, but never use tenant identity to filter global data.
        runtimeSessionState.requirePrincipal();
    }

    private static ReleaseChannel parseChannel(String value) {
        try {
            return ReleaseChannel.valueOf(value);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid release channel.");
        }
    }

    private static SemanticVersion parseVersion(String value) {
        try {
            return SemanticVersion.parse(value);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Version must use strict canonical major.minor.build format.");
        }
    }

    private ApplicationReleaseResponse toResponse(ApplicationReleaseView release, ReleaseChannel channel,
            SemanticVersion lowerBound) {
        if (release.publicationStatus() != PublicationStatus.PUBLISHED || release.channel() != channel
                || release.version().compareTo(lowerBound) <= 0) {
            throw new IllegalStateException("Release read boundary returned data outside the published query contract");
        }
        List<ApplicationReleaseItemResponse> items = releaseReadService.listReleaseItems(release.id()).stream()
                .filter(ApplicationReleaseItemView::active)
                .map(item -> toResponse(item, release.id()))
                .toList();
        return new ApplicationReleaseResponse(release.id(), release.version().toString(), release.channel(),
                release.publishedAt(), release.summary(), items);
    }

    private static ApplicationReleaseItemResponse toResponse(ApplicationReleaseItemView item, long releaseId) {
        if (item.releaseId() != releaseId) {
            throw new IllegalStateException("Release item read boundary returned an item for a different release");
        }
        return new ApplicationReleaseItemResponse(item.id(), item.sortOrder(), item.itemType(), item.title(),
                item.body(), item.resourceUrl());
    }

    private static ApplicationPolicyResponse toResponse(ApplicationPolicyView policy) {
        return new ApplicationPolicyResponse(policy.id(), policy.channel(), policy.revisionNumber(),
                version(policy.latest()), id(policy.latest()),
                version(policy.minimumRecommended()), id(policy.minimumRecommended()),
                version(policy.minimumAllowed()), id(policy.minimumAllowed()),
                policy.requiredUpdateDeadline(), policy.accessMode(), policy.publishedAt());
    }

    private static String version(ReleaseReference reference) {
        return reference == null ? null : reference.version().toString();
    }

    private static Long id(ReleaseReference reference) {
        return reference == null ? null : reference.releaseId();
    }
}
