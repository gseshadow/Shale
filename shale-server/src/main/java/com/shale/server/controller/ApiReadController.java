package com.shale.server.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactSummary;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.TaskServicePort;
import com.shale.server.dto.PagedResponse;
import com.shale.server.runtime.ServerRuntimeSessionState;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Read API", description = "Tenant-scoped read endpoints for the Shale web app")
@SecurityRequirement(name = "bearerAuth")
public final class ApiReadController {
    private static final int DEFAULT_SEARCH_LIMIT = 25;

    private final CaseServicePort caseServicePort;
    private final TaskServicePort taskServicePort;
    private final ContactServicePort contactServicePort;
    private final NotificationServicePort notificationServicePort;
    private final ServerRuntimeSessionState runtimeSessionState;

    public ApiReadController(
            CaseServicePort caseServicePort,
            TaskServicePort taskServicePort,
            ContactServicePort contactServicePort,
            NotificationServicePort notificationServicePort,
            ServerRuntimeSessionState runtimeSessionState) {
        this.caseServicePort = caseServicePort;
        this.taskServicePort = taskServicePort;
        this.contactServicePort = contactServicePort;
        this.notificationServicePort = notificationServicePort;
        this.runtimeSessionState = runtimeSessionState;
    }

    @Operation(summary = "Search cases", description = "Returns the first matching cases for the authenticated tenant. Preserved list response for existing clients.")
    @GetMapping("/api/cases/search")
    public List<CaseOverviewDto> searchCases(@RequestParam(name = "query", defaultValue = "") String query) {
        String safeQuery = ApiValidation.searchQuery(query);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.searchCases(safeQuery, shaleClientId, DEFAULT_SEARCH_LIMIT);
    }

    @Operation(summary = "Search cases with pagination metadata", description = "Returns a page-shaped response for web clients. total is null because no cheap count is currently available.")
    @GetMapping("/api/cases/search-page")
    public PagedResponse<CaseOverviewDto> searchCasesPage(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        String safeQuery = ApiValidation.searchQuery(query);
        int safePage = ApiValidation.page(page);
        int safeSize = ApiValidation.size(size);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        List<CaseOverviewDto> fetched = caseServicePort.searchCases(
                safeQuery,
                shaleClientId,
                ApiValidation.searchLimitForPage(safePage, safeSize));
        return new PagedResponse<>(slice(fetched, safePage, safeSize), safePage, safeSize, null);
    }

    @Operation(summary = "List my cases", description = "Returns cases assigned to the current authenticated user.")
    @GetMapping("/api/cases/assigned")
    public List<CaseOverviewDto> listAssignedCases() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        return caseServicePort.listAssignedCases(userId, shaleClientId, DEFAULT_SEARCH_LIMIT);
    }

    @Operation(summary = "List my tasks", description = "Returns active tasks assigned to the current authenticated user.")
    @GetMapping("/api/tasks/assigned")
    public List<CaseTaskListItemDto> listAssignedTasks() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        return taskServicePort.listAssignedTasks(userId, shaleClientId);
    }

    @Operation(summary = "Get case detail", description = "Returns one tenant-scoped case detail record.")
    @GetMapping("/api/cases/{caseId}")
    public Object getCase(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.getCaseDetail(safeCaseId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
    }

    @Operation(summary = "List case tasks", description = "Returns tasks for one tenant-scoped case.")
    @GetMapping("/api/cases/{caseId}/tasks")
    public List<CaseTaskListItemDto> listCaseTasks(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return taskServicePort.listCaseTasks(safeCaseId, shaleClientId);
    }

    @Operation(summary = "Search contacts", description = "Returns the first matching contacts for the authenticated tenant. Preserved list response for existing clients.")
    @GetMapping("/api/contacts/search")
    public List<ContactSummary> searchContacts(@RequestParam(name = "query", defaultValue = "") String query) {
        String safeQuery = ApiValidation.searchQuery(query);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return contactServicePort.searchContacts(shaleClientId, safeQuery, DEFAULT_SEARCH_LIMIT);
    }

    @Operation(summary = "Search contacts with pagination metadata", description = "Returns a page-shaped contact search response. total is null because no cheap count is currently available.")
    @GetMapping("/api/contacts/search-page")
    public PagedResponse<ContactSummary> searchContactsPage(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        String safeQuery = ApiValidation.searchQuery(query);
        int safePage = ApiValidation.page(page);
        int safeSize = ApiValidation.size(size);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        List<ContactSummary> fetched = contactServicePort.searchContacts(
                shaleClientId,
                safeQuery,
                ApiValidation.searchLimitForPage(safePage, safeSize));
        return new PagedResponse<>(slice(fetched, safePage, safeSize), safePage, safeSize, null);
    }

    @Operation(summary = "List unread notifications", description = "Returns unread notifications for the current authenticated user and tenant.")
    @GetMapping("/api/notifications/unread")
    public List<NotificationSummary> unreadNotifications() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        return notificationServicePort.listUnreadNotifications(shaleClientId, userId);
    }

    private static <T> List<T> slice(List<T> fetched, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        if (offset >= fetched.size()) {
            return List.of();
        }
        int endExclusive = Math.min(offset + size, fetched.size());
        return fetched.subList(offset, endExclusive);
    }
}
