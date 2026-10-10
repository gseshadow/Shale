package com.shale.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shale.core.dto.MinimizedCaseOverview;
import com.shale.core.dto.MinimizedCasePage;
import com.shale.core.service.CaseReadException;
import com.shale.core.service.CaseServicePort;
import com.shale.server.dto.ApiErrorResponse;
import com.shale.server.runtime.ServerRuntimeSessionState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/api/v2/cases", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Minimized Case reads")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid request or page boundary", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Confirmed session rejection", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Operation denied; retain the session", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "503", description = "Read, timeout, required audit or payload unavailable; no Case data", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public final class MinimizedCaseReadController {
    public record SearchRequest(
            @Schema(maxLength = 100, description = "Literal Case-name substring; blank returns no browse. Never put search text in URLs.") String query,
            @Schema(minimum = "0", maximum = "100", defaultValue = "0") Integer page,
            @Schema(minimum = "1", maximum = "25", defaultValue = "25") Integer size) {
        // Spring's request-body converter may format this object at DEBUG/TRACE.
        @Override public String toString() { return "MinimizedCaseSearchRequest[query=REDACTED]"; }
    }

    private final CaseServicePort cases;
    private final ServerRuntimeSessionState sessions;
    private final ObjectMapper json;

    public MinimizedCaseReadController(CaseServicePort cases, ServerRuntimeSessionState sessions, ObjectMapper json) {
        this.cases = cases; this.sessions = sessions; this.json = json;
    }

    @PostMapping(value = "/search-page", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search the eligible active tenant Case set before SQL paging", description = "Body-only name search. Pages 0–100, size 1–25; hasMore at page 100 signals the result-window ceiling. Summary reads are audit-exempt. UTF-8 JSON at most 128 KiB.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MinimizedCasePage.class)))
    public ResponseEntity<byte[]> search(@RequestBody SearchRequest request) {
        var principal = sessions.requirePrincipal();
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request.");
        String query = request.query() == null ? "" : request.query().strip();
        if (query.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be 100 characters or fewer.");
        int page = page(request.page()), size = size(request.size());
        return bounded(cases.searchMinimizedCases(query, principal.shaleClientId(), principal.userId(), page, size), 128 * 1024);
    }

    @GetMapping("/assigned-page")
    @Operation(summary = "Assigned selection before paging", description = "Assignment filters this list only; search and Overview are tenant-wide. Summary reads are audit-exempt. At page 100 hasMore still reflects the probe; Next must stop at the ceiling. UTF-8 JSON at most 128 KiB.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MinimizedCasePage.class)))
    public ResponseEntity<byte[]> assigned(
            @Schema(minimum = "0", maximum = "100") @RequestParam(defaultValue = "0") int page,
            @Schema(minimum = "1", maximum = "25") @RequestParam(defaultValue = "25") int size) {
        var principal = sessions.requirePrincipal();
        return bounded(cases.listMinimizedAssignedCases(principal.shaleClientId(), principal.userId(), page(page), size(size)), 128 * 1024);
    }

    @GetMapping("/{caseId}/overview")
    @Operation(summary = "Read one minimized active tenant Case with required committed Overview audit", description = "One authoritative Case.Overview.Read per successful server read. UTF-8 JSON at most 8 KiB. No broad detail/child hydration.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MinimizedCaseOverview.class)))
    @ApiResponse(responseCode = "404", description = "Case unavailable; missing, foreign and deleted are indistinguishable", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<byte[]> overview(@PathVariable long caseId) {
        var principal = sessions.requirePrincipal();
        if (caseId <= 0 || caseId > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Case ID.");
        var result = cases.readMinimizedCaseOverview(caseId, principal.shaleClientId(), principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case unavailable."));
        return bounded(result, 8 * 1024);
    }

    private static int page(Integer page) { return ApiValidation.page(page == null ? 0 : page); }
    private static int size(Integer size) {
        int value = size == null ? 25 : size;
        if (value < 1 || value > 25) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 25.");
        return value;
    }
    private ResponseEntity<byte[]> bounded(Object result, int maximumBytes) {
        try {
            // Deliver these exact checked bytes, avoiding a second serializer with different byte semantics.
            byte[] bytes = json.writeValueAsBytes(result);
            if (bytes.length > maximumBytes) throw new CaseReadException(CaseReadException.Kind.OVERSIZED);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(bytes);
        } catch (JsonProcessingException failure) { throw new CaseReadException(CaseReadException.Kind.READ_UNAVAILABLE, failure); }
    }
}
