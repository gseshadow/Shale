package com.shale.server.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.AddCaseNoteCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseCoreDetailsCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseStatusCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseAssignmentCommand;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactDetail;
import com.shale.core.service.ContactServicePort.ContactSummary;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationDetail;
import com.shale.core.service.OrganizationServicePort.OrganizationSummary;
import com.shale.core.service.OrganizationServicePort.UpdateOrganizationCommand;
import com.shale.core.service.TaskServicePort;
import com.shale.core.service.TaskServicePort.CreateTaskCommand;
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
    public record AddCaseUpdateRequest(String noteText) {
    }

    public record CreateCaseTaskRequest(String title, String description, String dueDate) {
    }

    public record UpdateCaseAssignmentRequest(Integer practiceAreaId, Integer responsibleAttorneyUserId) {
    }

    public record UpdateCaseCoreDetailsRequest(
            String caseName,
            String description,
            String dateOfInjury,
            String statuteOfLimitations,
            String tortNoticeDeadline,
            String summary,
            String expectedRowVer) {
    }

    public record UpdateCaseStatusRequest(Integer statusId) {
    }

    public record UpdateContactRequest(
            String name,
            String firstName,
            String lastName,
            String email,
            String phone,
            String addressHome,
            String dateOfBirth,
            String condition,
            Boolean deceased) {
    }

    public record UpdateOrganizationRequest(
            String name,
            String phone,
            String fax,
            String email,
            String website,
            String address1,
            String address2,
            String city,
            String state,
            String postalCode,
            String country,
            String notes) {
    }

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


    @Operation(summary = "Update case core details", description = "Updates safe core fields for one tenant-scoped case and returns the refreshed case detail.")
    @PatchMapping("/api/cases/{caseId:\\d+}/core-details")
    public CaseDetailDto updateCaseCoreDetails(
            @PathVariable("caseId") long caseId,
            @RequestBody UpdateCaseCoreDetailsRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        String caseName = ApiValidation.caseName(request == null ? null : request.caseName());
        String description = ApiValidation.optionalCaseDescription(request == null ? null : request.description());
        LocalDate dateOfInjury = parseOptionalIsoDate(request == null ? null : request.dateOfInjury(), "dateOfInjury");
        LocalDate statuteOfLimitations = parseOptionalIsoDate(request == null ? null : request.statuteOfLimitations(), "statuteOfLimitations");
        LocalDate tortNoticeDeadline = parseOptionalIsoDate(request == null ? null : request.tortNoticeDeadline(), "tortNoticeDeadline");
        String summary = ApiValidation.optionalCaseSummary(request == null ? null : request.summary());
        byte[] expectedRowVer = parseExpectedRowVer(request == null ? null : request.expectedRowVer());
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        CaseDetailDto current = caseServicePort.getCaseDetail(safeCaseId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        CaseDetailDto updated = caseServicePort.updateCaseCoreDetails(new UpdateCaseCoreDetailsCommand(
                safeCaseId,
                shaleClientId,
                userId,
                caseName,
                current.getCaseNumber(),
                description,
                dateOfInjury,
                statuteOfLimitations,
                tortNoticeDeadline,
                summary,
                expectedRowVer));
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case details were changed by someone else. Refresh and try again.");
        }
        return updated;
    }

    @Operation(summary = "Update case assignment", description = "Updates assignment/classification fields for one tenant-scoped case and returns the refreshed case detail.")
    @PatchMapping("/api/cases/{caseId:\\d+}/assignment")
    public CaseDetailDto updateCaseAssignment(
            @PathVariable("caseId") long caseId,
            @RequestBody UpdateCaseAssignmentRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        if (request == null || request.practiceAreaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "practiceAreaId is required.");
        }
        if (request.responsibleAttorneyUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responsibleAttorneyUserId is required.");
        }
        int practiceAreaId = Math.toIntExact(ApiValidation.positiveId(request.practiceAreaId(), "practiceAreaId"));
        int responsibleAttorneyUserId = Math.toIntExact(ApiValidation.positiveId(request.responsibleAttorneyUserId(), "responsibleAttorneyUserId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        CaseDetailDto updated = caseServicePort.updateCaseAssignment(new UpdateCaseAssignmentCommand(
                safeCaseId, shaleClientId, userId, practiceAreaId, responsibleAttorneyUserId));
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
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


    @Operation(summary = "Create case task", description = "Creates a task linked to one tenant-scoped case using the authenticated user as creator.")
    @PostMapping("/api/cases/{caseId:\\d+}/tasks")
    public List<CaseTaskListItemDto> createCaseTask(
            @PathVariable("caseId") long caseId,
            @RequestBody CreateCaseTaskRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        String title = ApiValidation.taskTitle(request == null ? null : request.title());
        String description = ApiValidation.optionalTaskDescription(request == null ? null : request.description());
        LocalDateTime dueAt = parseOptionalDueDate(request == null ? null : request.dueDate());
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        taskServicePort.createTaskWithDefaultStatus(new CreateTaskCommand(
                safeCaseId,
                shaleClientId,
                userId,
                title,
                description,
                dueAt,
                null,
                null));
        return taskServicePort.listCaseTasks(safeCaseId, shaleClientId);
    }

    @Operation(summary = "List case updates", description = "Returns notes/updates for one tenant-scoped case.")
    @GetMapping("/api/cases/{caseId:\\d+}/updates")
    public List<CaseUpdateDto> listCaseUpdates(@PathVariable("caseId") long caseId) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.listCaseUpdates(safeCaseId, shaleClientId);
    }


    @Operation(summary = "Add case update", description = "Adds a user-authored note/update to one tenant-scoped case.")
    @PostMapping("/api/cases/{caseId:\\d+}/updates")
    public List<CaseUpdateDto> addCaseUpdate(
            @PathVariable("caseId") long caseId,
            @RequestBody AddCaseUpdateRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        String noteText = ApiValidation.noteText(request == null ? null : request.noteText());
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        caseServicePort.addCaseNote(new AddCaseNoteCommand(safeCaseId, shaleClientId, userId, noteText));
        return caseServicePort.listCaseUpdates(safeCaseId, shaleClientId);
    }


    @Operation(summary = "List effective case statuses", description = "Returns active effective case statuses available to the authenticated tenant for case status changes.")
    @GetMapping("/api/lookups/case-statuses")
    public List<CaseStatusDto> listCaseStatusLookup() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.listCaseStatuses(shaleClientId, false);
    }

    @Operation(summary = "Update case status", description = "Changes the current status for one tenant-scoped case and returns the refreshed case detail.")
    @PatchMapping("/api/cases/{caseId:\\d+}/status")
    public CaseDetailDto updateCaseStatus(
            @PathVariable("caseId") long caseId,
            @RequestBody UpdateCaseStatusRequest request) {
        long safeCaseId = ApiValidation.positiveId(caseId, "caseId");
        int statusId = Math.toIntExact(ApiValidation.positiveId(request == null || request.statusId() == null ? 0 : request.statusId(), "statusId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        CaseDetailDto updated = caseServicePort.updateCaseCurrentStatus(new UpdateCaseStatusCommand(
                safeCaseId,
                shaleClientId,
                userId,
                statusId));
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
        return updated;
    }

    @Operation(summary = "Get task detail", description = "Returns one tenant-scoped task detail record.")
    @GetMapping("/api/tasks/{taskId:\\d+}")
    public TaskDetailDto getTask(@PathVariable("taskId") long taskId) {
        long safeTaskId = ApiValidation.positiveId(taskId, "taskId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return taskServicePort.getTaskDetail(safeTaskId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }


    @Operation(summary = "Complete task", description = "Marks one tenant-scoped task complete for the authenticated user.")
    @PatchMapping("/api/tasks/{taskId:\\d+}/complete")
    public TaskDetailDto completeTask(@PathVariable("taskId") long taskId) {
        long safeTaskId = ApiValidation.positiveId(taskId, "taskId");
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        taskServicePort.completeTask(safeTaskId, shaleClientId, userId);
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


    @Operation(summary = "Update contact detail", description = "Updates supported tenant-scoped contact fields and returns the refreshed contact detail.")
    @PatchMapping("/api/contacts/{contactId:\\d+}")
    public ContactDetail updateContact(@PathVariable("contactId") int contactId, @RequestBody UpdateContactRequest request) {
        int safeContactId = Math.toIntExact(ApiValidation.positiveId(contactId, "contactId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        String firstName = ApiValidation.optionalContactNamePart(request == null ? null : request.firstName(), "First name");
        String lastName = ApiValidation.optionalContactNamePart(request == null ? null : request.lastName(), "Last name");
        String displayName = ApiValidation.optionalContactDisplayName(request == null ? null : request.name());
        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank()) && (displayName == null || displayName.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least a display name, first name, or last name is required.");
        }
        String email = ApiValidation.optionalEmail(request == null ? null : request.email(), "Email");
        String phone = ApiValidation.optionalContactText(request == null ? null : request.phone(), "Phone", 100);
        String addressHome = ApiValidation.optionalContactText(request == null ? null : request.addressHome(), "Address", 2000);
        String dateOfBirth = ApiValidation.optionalDateText(request == null ? null : request.dateOfBirth(), "Date of birth");
        String condition = ApiValidation.optionalContactText(request == null ? null : request.condition(), "Notes", 10000);
        boolean updated = contactServicePort.updateContact(new ContactServicePort.UpdateContactCommand(
                safeContactId, shaleClientId, userId, displayName, firstName, lastName, email, phone, addressHome, dateOfBirth, condition, request == null ? null : request.deceased()));
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found.");
        }
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

    @Operation(summary = "Update organization detail", description = "Updates supported tenant-scoped organization fields and returns the refreshed organization detail.")
    @PatchMapping("/api/organizations/{organizationId:\\d+}")
    public OrganizationDetail updateOrganization(@PathVariable("organizationId") int organizationId, @RequestBody UpdateOrganizationRequest request) {
        int safeOrganizationId = Math.toIntExact(ApiValidation.positiveId(organizationId, "organizationId"));
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        String name = ApiValidation.organizationName(request == null ? null : request.name());
        String phone = ApiValidation.optionalOrganizationText(request == null ? null : request.phone(), "Phone", 100);
        String fax = ApiValidation.optionalOrganizationText(request == null ? null : request.fax(), "Fax", 100);
        String email = ApiValidation.optionalEmail(request == null ? null : request.email(), "Email");
        String website = ApiValidation.optionalOrganizationText(request == null ? null : request.website(), "Website", 500);
        String address1 = ApiValidation.optionalOrganizationText(request == null ? null : request.address1(), "Address line 1", 500);
        String address2 = ApiValidation.optionalOrganizationText(request == null ? null : request.address2(), "Address line 2", 500);
        String city = ApiValidation.optionalOrganizationText(request == null ? null : request.city(), "City", 200);
        String state = ApiValidation.optionalOrganizationText(request == null ? null : request.state(), "State", 100);
        String postalCode = ApiValidation.optionalOrganizationText(request == null ? null : request.postalCode(), "Zip", 100);
        String country = ApiValidation.optionalOrganizationText(request == null ? null : request.country(), "Country", 100);
        String notes = ApiValidation.optionalOrganizationText(request == null ? null : request.notes(), "Notes", 10000);
        boolean updated = organizationServicePort.updateOrganization(new UpdateOrganizationCommand(
                safeOrganizationId, shaleClientId, userId, name, phone, fax, email, website, address1, address2, city, state, postalCode, country, notes));
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found.");
        }
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

    @Operation(summary = "List practice area lookup values", description = "Returns active practice areas for the authenticated tenant.")
    @GetMapping("/api/lookups/practice-areas")
    public List<PracticeAreaDto> listPracticeAreaLookups() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.listPracticeAreas(shaleClientId, false);
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


    private static LocalDate parseOptionalIsoDate(String dateText, String fieldName) {
        String safeDateText = ApiValidation.optionalDateText(dateText, fieldName);
        if (safeDateText == null) {
            return null;
        }
        try {
            return LocalDate.parse(safeDateText);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid calendar date.");
        }
    }

    private static byte[] parseExpectedRowVer(String expectedRowVer) {
        String safeExpectedRowVer = expectedRowVer == null ? "" : expectedRowVer.trim();
        if (safeExpectedRowVer.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedRowVer is required.");
        }
        try {
            return Base64.getDecoder().decode(safeExpectedRowVer);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedRowVer must be base64 encoded.");
        }
    }

    private static LocalDateTime parseOptionalDueDate(String dueDate) {
        String safeDueDate = ApiValidation.optionalDateText(dueDate, "dueDate");
        if (safeDueDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(safeDueDate).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date must use YYYY-MM-DD format.");
        }
    }

    private void requireCurrentUserAdmin(int shaleClientId) {
        int userId = runtimeSessionState.requireUserId();
        UserDetail currentUser = userServicePort.getUserDetail(userId, shaleClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
        if (!currentUser.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
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
