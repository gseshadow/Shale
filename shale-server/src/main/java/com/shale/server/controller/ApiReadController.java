package com.shale.server.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactDetail;
import com.shale.core.service.ContactServicePort.ContactSummary;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationDetail;
import com.shale.core.service.OrganizationServicePort.OrganizationSummary;
import com.shale.core.service.TaskServicePort;
import com.shale.core.service.UserServicePort;
import com.shale.core.service.UserServicePort.UserDetail;
import com.shale.core.service.UserServicePort.UserSummary;
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
    private final OrganizationServicePort organizationServicePort;
    private final UserServicePort userServicePort;
    private final ServerRuntimeSessionState runtimeSessionState;

    public ApiReadController(
            CaseServicePort caseServicePort,
            TaskServicePort taskServicePort,
            ContactServicePort contactServicePort,
            NotificationServicePort notificationServicePort,
            OrganizationServicePort organizationServicePort,
            UserServicePort userServicePort,
            ServerRuntimeSessionState runtimeSessionState) {
        this.caseServicePort = caseServicePort;
        this.taskServicePort = taskServicePort;
        this.contactServicePort = contactServicePort;
        this.notificationServicePort = notificationServicePort;
        this.organizationServicePort = organizationServicePort;
        this.userServicePort = userServicePort;
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
    @GetMapping("/api/cases/{caseId:\\d+}")
    public Object getCase(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.getCaseDetail(safeCaseId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
    }

    @Operation(summary = "Update case core details", description = "Updates the limited editable case detail fields for the authenticated tenant.")
    @PatchMapping("/api/cases/{caseId:\\d+}")
    public Object updateCaseCoreDetails(@PathVariable("caseId") long caseId, @RequestBody CaseCoreDetailsRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        CaseCoreDetailsRequest safeRequest = requireValidCaseCoreDetails(request);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        var updated = caseServicePort.updateCaseCoreDetails(new CaseServicePort.UpdateCaseCoreDetailsCommand(
                safeCaseId,
                shaleClientId,
                userId,
                safeRequest.name().trim(),
                safeRequest.caseNumber(),
                nullIfBlank(safeRequest.description()),
                nullIfBlank(safeRequest.summary()),
                parseDate(safeRequest.dateOfInjury(), "dateOfInjury"),
                parseDate(safeRequest.statuteOfLimitations(), "statuteOfLimitations"),
                parseDate(safeRequest.tortNoticeDeadline(), "tortNoticeDeadline"),
                decodeRowVer(safeRequest.rowVer())));
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case was changed by another user. Refresh and try again.");
        }
        return updated;
    }

    @Operation(summary = "List case tasks", description = "Returns tasks for one tenant-scoped case.")
    @GetMapping("/api/cases/{caseId:\\d+}/tasks")
    public List<CaseTaskListItemDto> listCaseTasks(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return taskServicePort.listCaseTasks(safeCaseId, shaleClientId);
    }

    @Operation(summary = "List case updates", description = "Returns notes/updates for one tenant-scoped case.")
    @GetMapping("/api/cases/{caseId:\\d+}/updates")
    public List<CaseUpdateDto> listCaseUpdates(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.listCaseUpdates(safeCaseId, shaleClientId);
    }

    @Operation(summary = "Get task detail", description = "Returns one tenant-scoped task detail record.")
    @GetMapping("/api/tasks/{taskId:\\d+}")
    public TaskDetailDto getTask(@PathVariable("taskId") long taskId) {
        long safeTaskId = ApiValidation.positiveId(taskId, "taskId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return taskServicePort.getTaskDetail(safeTaskId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }

    @Operation(summary = "Get contact detail", description = "Returns one tenant-scoped contact detail record.")
    @GetMapping("/api/contacts/{contactId:\\d+}")
    public ContactDetail getContact(@PathVariable("contactId") int contactId) {
        int safeContactId = Math.toIntExact(ApiValidation.positiveId(contactId, "contactId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return contactServicePort.getContactDetail(safeContactId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found."));
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


    @Operation(summary = "Search organizations", description = "Returns the first matching organizations for the authenticated tenant.")
    @GetMapping("/api/organizations/search")
    public List<OrganizationSummary> searchOrganizations(@RequestParam(name = "query", defaultValue = "") String query) {
        String safeQuery = ApiValidation.searchQuery(query);
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return organizationServicePort.searchOrganizations(shaleClientId, safeQuery, DEFAULT_SEARCH_LIMIT);
    }

    @Operation(summary = "Get organization detail", description = "Returns one tenant-scoped organization detail record.")
    @GetMapping("/api/organizations/{organizationId:\\d+}")
    public OrganizationDetail getOrganization(@PathVariable("organizationId") int organizationId) {
        int safeOrganizationId = Math.toIntExact(ApiValidation.positiveId(organizationId, "organizationId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return organizationServicePort.getOrganizationDetail(safeOrganizationId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found."));
    }


    @Operation(summary = "List case statuses", description = "Returns read-only case status settings for administrators in the authenticated tenant.")
    @GetMapping("/api/settings/case-statuses")
    public List<CaseStatusDto> listCaseStatuses() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        requireCurrentUserAdmin(shaleClientId);
        return caseServicePort.listTenantCaseStatuses(shaleClientId, true);
    }

    @Operation(summary = "List practice areas", description = "Returns read-only practice area settings for administrators in the authenticated tenant.")
    @GetMapping("/api/settings/practice-areas")
    public List<PracticeAreaDto> listPracticeAreas() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        requireCurrentUserAdmin(shaleClientId);
        return caseServicePort.listPracticeAreas(shaleClientId, true);
    }

    @Operation(summary = "List team members", description = "Returns visible users for the authenticated tenant.")
    @GetMapping("/api/users")
    public List<UserSummary> listTeamMembers() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return userServicePort.listTenantUsers(shaleClientId);
    }

    @Operation(summary = "Get team member detail", description = "Returns one tenant-scoped user profile.")
    @GetMapping("/api/users/{userId:\\d+}")
    public UserDetail getTeamMember(@PathVariable("userId") int userId) {
        int safeUserId = Math.toIntExact(ApiValidation.positiveId(userId, "userId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return userServicePort.getUserDetail(safeUserId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    @Operation(summary = "List unread notifications", description = "Returns unread notifications for the current authenticated user and tenant.")
    @GetMapping("/api/notifications/unread")
    public List<NotificationSummary> unreadNotifications() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        return notificationServicePort.listUnreadNotifications(shaleClientId, userId);
    }

    private void requireCurrentUserAdmin(int shaleClientId) {
        int userId = runtimeSessionState.requireUserId();
        UserDetail currentUser = userServicePort.getUserDetail(userId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
        if (!currentUser.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }

    private static CaseCoreDetailsRequest requireValidCaseCoreDetails(CaseCoreDetailsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case update body is required.");
        }
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case name is required.");
        }
        requireMaxLength(request.name().trim(), 400, "Case name");
        requireMaxLength(request.description(), 8000, "Description");
        requireMaxLength(request.summary(), 8000, "Summary");
        parseDate(request.dateOfInjury(), "dateOfInjury");
        parseDate(request.statuteOfLimitations(), "statuteOfLimitations");
        parseDate(request.tortNoticeDeadline(), "tortNoticeDeadline");
        decodeRowVer(request.rowVer());
        return request;
    }

    private static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is too long.");
        }
    }

    private static LocalDate parseDate(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be an ISO date.");
        }
    }

    private static byte[] decodeRowVer(String rowVer) {
        if (rowVer == null || rowVer.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rowVer is required.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(rowVer.trim());
            if (decoded.length == 0) {
                throw new IllegalArgumentException("empty row version");
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rowVer is not valid.");
        }
    }

    private static String nullIfBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public record CaseCoreDetailsRequest(
            String name,
            String caseNumber,
            String description,
            String summary,
            String dateOfInjury,
            String statuteOfLimitations,
            String tortNoticeDeadline,
            String rowVer) {
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
