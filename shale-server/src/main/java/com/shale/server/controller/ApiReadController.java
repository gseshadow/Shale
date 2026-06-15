package com.shale.server.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactSummary;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.TaskServicePort;
import com.shale.server.runtime.ServerRuntimeSessionState;

@RestController
public final class ApiReadController {
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

    @GetMapping("/api/cases/search")
    public List<CaseOverviewDto> searchCases(@RequestParam(name = "query", defaultValue = "") String query) {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.searchCases(query, shaleClientId, 25);
    }

    @GetMapping("/api/cases/{caseId}")
    public Object getCase(@PathVariable("caseId") long caseId) {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return caseServicePort.getCaseDetail(caseId, shaleClientId).orElseThrow();
    }

    @GetMapping("/api/cases/{caseId}/tasks")
    public List<CaseTaskListItemDto> listCaseTasks(@PathVariable("caseId") long caseId) {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return taskServicePort.listCaseTasks(caseId, shaleClientId);
    }

    @GetMapping("/api/contacts/search")
    public List<ContactSummary> searchContacts(@RequestParam(name = "query", defaultValue = "") String query) {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        return contactServicePort.searchContacts(shaleClientId, query, 25);
    }

    @GetMapping("/api/notifications/unread")
    public List<NotificationSummary> unreadNotifications() {
        int shaleClientId = runtimeSessionState.requireShaleClientId();
        int userId = runtimeSessionState.requireUserId();
        return notificationServicePort.listUnreadNotifications(shaleClientId, userId);
    }
}
