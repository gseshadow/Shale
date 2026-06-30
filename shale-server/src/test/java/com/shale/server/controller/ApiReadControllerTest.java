package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.NotificationServicePort.CalendarEventNotificationCommand;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.NotificationServicePort.TaskActionNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskDueDateNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskNotificationCommand;
import com.shale.core.service.TaskServicePort;
import com.shale.core.service.TaskServicePort.CreateTaskCommand;
import com.shale.core.service.UserServicePort;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.InMemoryTokenRevocationStore;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ShaleAuthTokenService;

import jakarta.servlet.http.HttpServletRequest;

class ApiReadControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiReadController apiReadController = new ApiReadController(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class),
                unusedPort(OrganizationServicePort.class),
                unusedPort(UserServicePort.class),
                new ServerRuntimeSessionState());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(), apiReadController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedPort(Class<T> portType) {
        return (T) Proxy.newProxyInstance(
                portType.getClassLoader(),
                new Class<?>[] {portType},
                (proxy, method, args) -> {
                    throw new AssertionError("DB-backed service port should not be called before server session context exists: "
                            + method.getName());
                });
    }


    private static MockMvc developmentMockMvc(
            CaseServicePort caseServicePort,
            TaskServicePort taskServicePort,
            ContactServicePort contactServicePort,
            NotificationServicePort notificationServicePort) {
        ApiReadController apiReadController = new ApiReadController(
                caseServicePort,
                taskServicePort,
                contactServicePort,
                notificationServicePort,
                unusedPort(OrganizationServicePort.class),
                unusedPort(UserServicePort.class),
                new ServerRuntimeSessionState(new DevelopmentHeaderServerSessionResolver(), currentRequestProvider()));
        return MockMvcBuilders
                .standaloneSetup(apiReadController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }


    private static MockMvc tokenMockMvc(
            ShaleAuthTokenService tokenService,
            CaseServicePort caseServicePort,
            TaskServicePort taskServicePort,
            ContactServicePort contactServicePort,
            NotificationServicePort notificationServicePort) {
        ApiReadController apiReadController = new ApiReadController(
                caseServicePort,
                taskServicePort,
                contactServicePort,
                notificationServicePort,
                unusedPort(OrganizationServicePort.class),
                unusedPort(UserServicePort.class),
                new ServerRuntimeSessionState(new BearerTokenServerSessionResolver(tokenService, new InMemoryTokenRevocationStore()), currentRequestProvider()));
        return MockMvcBuilders
                .standaloneSetup(apiReadController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }


    @Test
    void caseSearchReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/search")
                .param("query", "smith")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(501))
                .andExpect(jsonPath("$[0].caseName").value("Smith v. Example"))
                .andExpect(jsonPath("$[0].caseNumber").value("CASE-501"));

        org.junit.jupiter.api.Assertions.assertEquals("smith", caseServicePort.searchQuery);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.searchShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(25, caseServicePort.searchLimit);
    }


    @Test
    void protectedCaseSearchAcceptsRealBearerTokenPrincipal() throws Exception {
        ShaleAuthTokenService tokenService = new ShaleAuthTokenService(
                "test-auth-token-secret-that-is-long-enough", 3600, java.time.Clock.systemUTC());
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc tokenMockMvc = tokenMockMvc(
                tokenService,
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));
        String token = tokenService.issue(new ServerPrincipal(31, 41, "ada@example.test"));

        tokenMockMvc.perform(get("/api/cases/search")
                .param("query", "smith")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(501));

        org.junit.jupiter.api.Assertions.assertEquals("smith", caseServicePort.searchQuery);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.searchShaleClientId);
    }

    @Test
    void assignedCasesRouteDoesNotFallThroughToCaseIdRoute() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/assigned")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(501));

        org.junit.jupiter.api.Assertions.assertEquals(31, caseServicePort.assignedUserId);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.assignedShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(25, caseServicePort.assignedLimit);
        org.junit.jupiter.api.Assertions.assertEquals(0L, caseServicePort.detailCaseId);
    }

    @Test
    void assignedTasksRouteReachesAssignedTaskService() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/tasks/assigned")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].title").value("Review records"));

        org.junit.jupiter.api.Assertions.assertEquals(31, taskServicePort.assignedUserId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.assignedShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(0L, taskServicePort.caseId);
    }


    @Test
    void updateCaseCoreDetailsUsesSessionTenantAndReturnsUpdatedDetail() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(patch("/api/cases/501/core-details")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "caseName": "Smith v. Updated",
                          "description": "Updated detail",
                          "dateOfInjury": "2026-02-03",
                          "statuteOfLimitations": "2027-02-03",
                          "tortNoticeDeadline": "2026-08-09",
                          "summary": "Updated summary",
                          "expectedRowVer": "AQ=="
                        }
                        """)
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(501))
                .andExpect(jsonPath("$.caseName").value("Smith v. Updated"))
                .andExpect(jsonPath("$.description").value("Updated detail"))
                .andExpect(jsonPath("$.tortNoticeDeadline[0]").value(2026))
                .andExpect(jsonPath("$.tortNoticeDeadline[1]").value(8))
                .andExpect(jsonPath("$.tortNoticeDeadline[2]").value(9))
                .andExpect(jsonPath("$.summary").value("Updated summary"));

        org.junit.jupiter.api.Assertions.assertEquals(501L, caseServicePort.updateCaseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.updateShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(31, caseServicePort.updateActorUserId);
        org.junit.jupiter.api.Assertions.assertEquals("Smith v. Updated", caseServicePort.updateCaseName);
        org.junit.jupiter.api.Assertions.assertEquals("CASE-501", caseServicePort.updateCaseNumber);
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 2, 3), caseServicePort.updateDateOfInjury);
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2027, 2, 3), caseServicePort.updateStatuteOfLimitations);
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 8, 9), caseServicePort.updateTortNoticeDeadline);
        org.junit.jupiter.api.Assertions.assertEquals("Updated summary", caseServicePort.updateSummary);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[] {1}, caseServicePort.updateExpectedRowVer);
    }

    @Test
    void taskDetailReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/tasks/701")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(701))
                .andExpect(jsonPath("$.title").value("Review records"))
                .andExpect(jsonPath("$.caseId").value(501));

        org.junit.jupiter.api.Assertions.assertEquals(701L, taskServicePort.detailTaskId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.detailShaleClientId);
    }


    @Test
    void updateTaskRouteReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(patch("/api/tasks/701")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated task\",\"description\":\"Updated notes\",\"dueDate\":\"2026-03-04\",\"priorityId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(701));

        org.junit.jupiter.api.Assertions.assertNotNull(taskServicePort.updatedCommand);
        org.junit.jupiter.api.Assertions.assertEquals(701L, taskServicePort.updatedCommand.taskId());
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.updatedCommand.shaleClientId());
        org.junit.jupiter.api.Assertions.assertEquals(31, taskServicePort.updatedCommand.actorUserId());
        org.junit.jupiter.api.Assertions.assertEquals("Updated task", taskServicePort.updatedCommand.title());
        org.junit.jupiter.api.Assertions.assertEquals("Updated notes", taskServicePort.updatedCommand.description());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 3, 4, 0, 0), taskServicePort.updatedCommand.dueAt());
        org.junit.jupiter.api.Assertions.assertEquals(2, taskServicePort.updatedCommand.statusId());
        org.junit.jupiter.api.Assertions.assertEquals(1, taskServicePort.updatedCommand.priorityId());
    }


    @Test
    void completeTaskRouteReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(patch("/api/tasks/701/complete")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(701))
                .andExpect(jsonPath("$.title").value("Review records"));

        org.junit.jupiter.api.Assertions.assertEquals(701L, taskServicePort.completedTaskId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.completedShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(31, taskServicePort.completedActorUserId);
        org.junit.jupiter.api.Assertions.assertEquals(701L, taskServicePort.detailTaskId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.detailShaleClientId);
    }

    @Test
    void caseDetailReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/501")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(501))
                .andExpect(jsonPath("$.caseName").value("Smith v. Example"))
                .andExpect(jsonPath("$.caseNumber").value("CASE-501"))
                .andExpect(jsonPath("$.responsibleAttorney").value("Ada Attorney"));

        org.junit.jupiter.api.Assertions.assertEquals(501L, caseServicePort.detailCaseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.detailShaleClientId);
    }

    @Test
    void caseTasksReachServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/501/tasks")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].shaleClientId").value(41))
                .andExpect(jsonPath("$[0].caseId").value(501))
                .andExpect(jsonPath("$[0].title").value("Review records"));

        org.junit.jupiter.api.Assertions.assertEquals(501L, taskServicePort.caseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.shaleClientId);
    }


    @Test
    void createCaseTaskReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(post("/api/cases/501/tasks")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  Review records  \",\"description\":\"  Read intake packet  \",\"dueDate\":\"2026-01-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].caseId").value(501))
                .andExpect(jsonPath("$[0].title").value("Review records"));

        org.junit.jupiter.api.Assertions.assertEquals(501L, taskServicePort.createdCommand.caseId());
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.createdCommand.shaleClientId());
        org.junit.jupiter.api.Assertions.assertEquals(31, taskServicePort.createdCommand.createdByUserId());
        org.junit.jupiter.api.Assertions.assertEquals("Review records", taskServicePort.createdCommand.title());
        org.junit.jupiter.api.Assertions.assertEquals("Read intake packet", taskServicePort.createdCommand.description());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 1, 2, 0, 0), taskServicePort.createdCommand.dueAt());
        org.junit.jupiter.api.Assertions.assertNull(taskServicePort.createdCommand.priorityId());
        org.junit.jupiter.api.Assertions.assertNull(taskServicePort.createdCommand.assignedUserId());
        org.junit.jupiter.api.Assertions.assertEquals(501L, taskServicePort.caseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, taskServicePort.shaleClientId);
    }

    @Test
    void createCaseTaskRejectsBlankTitle() throws Exception {
        RecordingTaskServicePort taskServicePort = new RecordingTaskServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                taskServicePort,
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(post("/api/cases/501/tasks")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"dueDate\":\"2026-01-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Task title is required")));

        org.junit.jupiter.api.Assertions.assertNull(taskServicePort.createdCommand);
    }

    @Test
    void caseUpdatesReachServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/501/updates")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(901))
                .andExpect(jsonPath("$[0].caseId").value(501))
                .andExpect(jsonPath("$[0].noteText").value("Called client."))
                .andExpect(jsonPath("$[0].createdByDisplayName").value("Ada Attorney"));

        org.junit.jupiter.api.Assertions.assertEquals(501L, caseServicePort.updatesCaseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.updatesShaleClientId);
    }


    @Test
    void addCaseUpdateReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(post("/api/cases/501/updates")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteText\":\"  Called client.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(901))
                .andExpect(jsonPath("$[0].noteText").value("Called client."));

        org.junit.jupiter.api.Assertions.assertEquals(501L, caseServicePort.addNoteCaseId);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.addNoteShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(31, caseServicePort.addNoteActorUserId);
        org.junit.jupiter.api.Assertions.assertEquals("Called client.", caseServicePort.addNoteText);
    }

    @Test
    void addCaseUpdateRejectsBlankNoteText() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(post("/api/cases/501/updates")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Note text is required")));

        org.junit.jupiter.api.Assertions.assertNull(caseServicePort.addNoteText);
    }

    @Test
    void contactSearchReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingContactServicePort contactServicePort = new RecordingContactServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                contactServicePort,
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/contacts/search")
                .param("query", "ada")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(801))
                .andExpect(jsonPath("$[0].displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].email").value("ada@example.test"));

        org.junit.jupiter.api.Assertions.assertEquals("ada", contactServicePort.query);
        org.junit.jupiter.api.Assertions.assertEquals(41, contactServicePort.shaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(25, contactServicePort.limit);
    }

    @Test
    void contactDetailReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingContactServicePort contactServicePort = new RecordingContactServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                contactServicePort,
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/contacts/801")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(801))
                .andExpect(jsonPath("$.shaleClientId").value(41))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.phone").value("555-0100"));

        org.junit.jupiter.api.Assertions.assertEquals(801, contactServicePort.contactId);
        org.junit.jupiter.api.Assertions.assertEquals(41, contactServicePort.detailShaleClientId);
    }

    @Test
    void missingContactDetailReturnsNotFoundWithDevelopmentHeaders() throws Exception {
        RecordingContactServicePort contactServicePort = new RecordingContactServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                contactServicePort,
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/contacts/404")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/contacts/404"));
    }

    @Test
    void unreadNotificationsReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingNotificationServicePort notificationServicePort = new RecordingNotificationServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                notificationServicePort);

        devMockMvc.perform(get("/api/notifications/unread")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(31))
                .andExpect(jsonPath("$[0].shaleClientId").value(41))
                .andExpect(jsonPath("$[0].title").value("Development proof notification"));

        org.junit.jupiter.api.Assertions.assertEquals(31, notificationServicePort.userId);
        org.junit.jupiter.api.Assertions.assertEquals(41, notificationServicePort.shaleClientId);
    }

    @Test
    void caseSearchPageReturnsPageContractWithDevelopmentHeaders() throws Exception {
        RecordingCaseServicePort caseServicePort = new RecordingCaseServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                caseServicePort,
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/cases/search-page")
                .param("query", "smith")
                .param("page", "1")
                .param("size", "1")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].caseId").value(502))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals("smith", caseServicePort.searchQuery);
        org.junit.jupiter.api.Assertions.assertEquals(41, caseServicePort.searchShaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(2, caseServicePort.searchLimit);
    }

    @Test
    void contactSearchPageReturnsPageContractWithDevelopmentHeaders() throws Exception {
        RecordingContactServicePort contactServicePort = new RecordingContactServicePort();
        MockMvc devMockMvc = developmentMockMvc(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                contactServicePort,
                unusedPort(NotificationServicePort.class));

        devMockMvc.perform(get("/api/contacts/search-page")
                .param("query", "ada")
                .param("page", "0")
                .param("size", "1")
                .header(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "31")
                .header(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(801))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals("ada", contactServicePort.query);
        org.junit.jupiter.api.Assertions.assertEquals(41, contactServicePort.shaleClientId);
        org.junit.jupiter.api.Assertions.assertEquals(1, contactServicePort.limit);
    }

    @Test
    void invalidSearchInputReturnsStandardizedBadRequestBeforeDbAccess() throws Exception {
        mockMvc.perform(get("/api/cases/search").param("query", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Search query must be 100 characters or fewer."))
                .andExpect(jsonPath("$.path").value("/api/cases/search"));
    }

    @Test
    void invalidCaseIdReturnsStandardizedBadRequestBeforeDbAccess() throws Exception {
        mockMvc.perform(get("/api/cases/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("caseId must be positive."))
                .andExpect(jsonPath("$.path").value("/api/cases/0"));
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


    private static final class RecordingCaseServicePort implements CaseServicePort {
        private String searchQuery;
        private int searchShaleClientId;
        private int searchLimit;
        private long detailCaseId;
        private int detailShaleClientId;
        private int assignedUserId;
        private int assignedShaleClientId;
        private int assignedLimit;
        private long updatesCaseId;
        private int updatesShaleClientId;
        private long addNoteCaseId;
        private int addNoteShaleClientId;
        private int addNoteActorUserId;
        private String addNoteText;
        private long updateCaseId;
        private int updateShaleClientId;
        private int updateActorUserId;
        private String updateCaseName;
        private String updateCaseNumber;
        private LocalDate updateDateOfInjury;
        private LocalDate updateStatuteOfLimitations;
        private LocalDate updateTortNoticeDeadline;
        private String updateSummary;
        private byte[] updateExpectedRowVer;

        @Override
        public Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId) {
            this.detailCaseId = caseId;
            this.detailShaleClientId = shaleClientId;
            return Optional.of(new CaseDetailDto(caseId, "CASE-501", "Smith v. Example", "Detail", "Open", "Ada Attorney", 10,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, LocalDateTime.of(2026, 1, 1, 0, 0), new byte[] {1}));
        }

        @Override
        public Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId) {
            throw new AssertionError("getCaseOverview should not be called");
        }

        @Override
        public List<CaseOverviewDto> searchCases(String query, int shaleClientId, int limit) {
            this.searchQuery = query;
            this.searchShaleClientId = shaleClientId;
            this.searchLimit = limit;
            return List.of(caseOverview(), secondCaseOverview());
        }

        @Override
        public List<CaseOverviewDto> listAssignedCases(int assignedUserId, int shaleClientId, int limit) {
            this.assignedUserId = assignedUserId;
            this.assignedShaleClientId = shaleClientId;
            this.assignedLimit = limit;
            return List.of(caseOverview());
        }

        @Override
        public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
            this.updatesCaseId = caseId;
            this.updatesShaleClientId = shaleClientId;
            return List.of(new CaseUpdateDto(901L, caseId, "Called client.",
                    LocalDateTime.of(2026, 6, 12, 15, 30),
                    null, 31, "Ada Attorney"));
        }

        @Override
        public List<com.shale.core.dto.CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
            return List.of(new com.shale.core.dto.CaseStatusDto(1, "Open", false, 10, "#00AA00", null, "open", null));
        }

        @Override
        public List<com.shale.core.dto.CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
            throw new AssertionError("listTenantCaseStatuses should not be called");
        }

        @Override
        public List<com.shale.core.dto.PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
            throw new AssertionError("listPracticeAreas should not be called");
        }

        @Override
        public List<com.shale.core.dto.PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
            throw new AssertionError("listTenantPracticeAreas should not be called");
        }

        @Override
        public com.shale.core.dto.PracticeAreaDto createPracticeArea(PracticeAreaCommand command) {
            throw new AssertionError("createPracticeArea should not be called");
        }

        @Override
        public com.shale.core.dto.PracticeAreaDto updatePracticeArea(PracticeAreaCommand command) {
            throw new AssertionError("updatePracticeArea should not be called");
        }

        @Override
        public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
            throw new AssertionError("deactivatePracticeArea should not be called");
        }

        @Override
        public com.shale.core.dto.CaseStatusDto createCaseStatus(CaseStatusCommand command) {
            throw new AssertionError("createCaseStatus should not be called");
        }

        @Override
        public com.shale.core.dto.CaseStatusDto updateCaseStatus(CaseStatusCommand command) {
            throw new AssertionError("updateCaseStatus should not be called");
        }

        @Override
        public void addCaseNote(AddCaseNoteCommand command) {
            this.addNoteCaseId = command.caseId();
            this.addNoteShaleClientId = command.shaleClientId();
            this.addNoteActorUserId = command.actorUserId();
            this.addNoteText = command.noteText();
        }

        @Override
        public CaseDetailDto updateCaseAssignment(UpdateCaseAssignmentCommand command) {
            return getCaseDetail(command.caseId(), command.shaleClientId()).orElseThrow();
        }

        @Override
        public CaseDetailDto updateCaseCoreDetails(UpdateCaseCoreDetailsCommand command) {
            this.updateCaseId = command.caseId();
            this.updateShaleClientId = command.shaleClientId();
            this.updateActorUserId = command.actorUserId();
            this.updateCaseName = command.caseName();
            this.updateCaseNumber = command.caseNumber();
            this.updateDateOfInjury = command.dateOfInjury();
            this.updateStatuteOfLimitations = command.statuteOfLimitations();
            this.updateTortNoticeDeadline = command.tortNoticeDeadline();
            this.updateSummary = command.summary();
            this.updateExpectedRowVer = command.expectedRowVer();
            return new CaseDetailDto(command.caseId(), command.caseNumber(), command.caseName(), command.description(), "Open", "Ada Attorney", 10,
                    null, null, null, null, null, null, null, command.dateOfInjury(), command.statuteOfLimitations(), command.tortNoticeDeadline(), null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, command.summary(), null, LocalDateTime.of(2026, 1, 2, 0, 0), new byte[] {2});
        }

        @Override
        public CaseDetailDto updateCaseCurrentStatus(UpdateCaseStatusCommand command) {
            return new CaseDetailDto(command.caseId(), "CASE-501", "Smith v. Example", "Detail", "Closed", "Ada Attorney", 10,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, LocalDateTime.of(2026, 1, 3, 0, 0), new byte[] {3});
        }

        @Override
        public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
            throw new AssertionError("reorderCaseStatuses should not be called");
        }

        private static CaseOverviewDto caseOverview() {
            return caseOverview(501L, "CASE-501");
        }

        private static CaseOverviewDto secondCaseOverview() {
            return caseOverview(502L, "CASE-502");
        }

        private static CaseOverviewDto caseOverview(long caseId, String caseNumber) {
            return new CaseOverviewDto(caseId, caseNumber, "Smith v. Example", "Open", 1, "#00AA00",
                    31, "Ada Attorney", "#111111", 10, "PI", "#222222",
                    null, null, null, null, null, null, "Caller", "Client", List.of(),
                    "Opposing", List.of("Ada Attorney"), "Overview");
        }
    }

    private static final class RecordingTaskServicePort implements TaskServicePort {
        private long caseId;
        private int shaleClientId;
        private int assignedUserId;
        private int assignedShaleClientId;
        private long detailTaskId;
        private int detailShaleClientId;
        private long completedTaskId;
        private int completedShaleClientId;
        private int completedActorUserId;
        private CreateTaskCommand createdCommand;
        private TaskServicePort.UpdateTaskCommand updatedCommand;

        @Override
        public List<CaseTaskListItemDto> listCaseTasks(long caseId, int shaleClientId) {
            this.caseId = caseId;
            this.shaleClientId = shaleClientId;
            return List.of(new CaseTaskListItemDto(701L, shaleClientId, caseId, "Smith v. Example",
                    "Open", "#00AA00", "#222222", "Ada Attorney", "#111111", false, "Review records", "Read intake packet",
                    1, "#FFAA00", LocalDateTime.of(2026, 1, 2, 12, 0), null,
                    31, "Ada Attorney", "#111111", 32, "Case Creator",
                    LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0), false));
        }

        @Override
        public List<CaseTaskListItemDto> listAssignedTasks(int assignedUserId, int shaleClientId) {
            this.assignedUserId = assignedUserId;
            this.assignedShaleClientId = shaleClientId;
            return List.of(new CaseTaskListItemDto(701L, shaleClientId, 501L, "Smith v. Example",
                    "Open", "#00AA00", "#222222", "Ada Attorney", "#111111", false, "Review records", "Read intake packet",
                    1, "#FFAA00", LocalDateTime.of(2026, 1, 2, 12, 0), null,
                    assignedUserId, "Ada Attorney", "#111111", 32, "Case Creator",
                    LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0), false));
        }

        @Override
        public Optional<TaskDetailDto> getTaskDetail(long taskId, int shaleClientId) {
            this.detailTaskId = taskId;
            this.detailShaleClientId = shaleClientId;
            return Optional.of(new TaskDetailDto(taskId, shaleClientId, 501L, "Smith v. Example",
                    "Ada Attorney", "#111111", false, "Review records", "Read intake packet",
                    LocalDateTime.of(2026, 1, 2, 12, 0), 2, 1, null,
                    31, "Ada Attorney", "#111111", "Case Creator"));
        }

        @Override
        public List<com.shale.core.dto.TaskPriorityOptionDto> listPriorities(int shaleClientId) {
            throw new AssertionError("listPriorities should not be called");
        }

        @Override
        public List<com.shale.core.dto.TaskStatusOptionDto> listStatuses(int shaleClientId) {
            throw new AssertionError("listStatuses should not be called");
        }

        @Override
        public long createTaskWithDefaultStatus(CreateTaskCommand command) {
            this.createdCommand = command;
            return 701L;
        }

        @Override
        public void updateTask(UpdateTaskCommand command) {
            this.updatedCommand = command;
        }

        @Override
        public void completeTask(long taskId, int shaleClientId, int actorUserId) {
            this.completedTaskId = taskId;
            this.completedShaleClientId = shaleClientId;
            this.completedActorUserId = actorUserId;
        }

        @Override
        public void assignTask(long taskId, int shaleClientId, int userId, int assignedByUserId) {
            throw new AssertionError("assignTask should not be called");
        }

        @Override
        public void removeTaskAssignment(long taskId, int shaleClientId, int userId, int actorUserId) {
            throw new AssertionError("removeTaskAssignment should not be called");
        }
    }

    private static final class RecordingContactServicePort implements ContactServicePort {
        private int shaleClientId;
        private String query;
        private int limit;
        private int contactId;
        private int detailShaleClientId;

        @Override
        public List<ContactSummary> searchContacts(int shaleClientId, String query, int limit) {
            this.shaleClientId = shaleClientId;
            this.query = query;
            this.limit = limit;
            return List.of(new ContactSummary(801, "Ada Lovelace", "ada@example.test", "555-0100"));
        }

        @Override
        public Optional<ContactDetail> getContactDetail(int contactId, int shaleClientId) {
            this.contactId = contactId;
            this.detailShaleClientId = shaleClientId;
            if (contactId == 404) {
                return Optional.empty();
            }
            return Optional.of(new ContactDetail(contactId, shaleClientId, "Ada Lovelace", "Ada", "Lovelace",
                    "Ada Lovelace", "ada@example.test", "555-0100", "123 Main", "1980-01-02", "Notes", false, true));
        }

        @Override
        public int createContact(CreateContactCommand command) {
            throw new AssertionError("createContact should not be called");
        }

        @Override
        public boolean updateContact(UpdateContactCommand command) {
            throw new AssertionError("updateContact should not be called");
        }

        @Override
        public boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId) {
            throw new AssertionError("softDeleteContact should not be called");
        }
    }

    private static final class RecordingNotificationServicePort implements NotificationServicePort {
        private int shaleClientId;
        private int userId;

        @Override
        public List<NotificationSummary> listUnreadNotifications(int shaleClientId, int userId) {
            this.shaleClientId = shaleClientId;
            this.userId = userId;
            return List.of(new NotificationSummary(100L, shaleClientId, userId, "INFO",
                    "Development proof notification", "Request context reached service layer.", Instant.EPOCH));
        }

        @Override
        public void markRead(int shaleClientId, int userId, long notificationId) {
            throw new AssertionError("markRead should not be called");
        }

        @Override
        public void dismiss(int shaleClientId, int userId, long notificationId) {
            throw new AssertionError("dismiss should not be called");
        }

        @Override
        public Optional<Long> createTaskAssignedNotification(TaskNotificationCommand command) {
            throw new AssertionError("createTaskAssignedNotification should not be called");
        }

        @Override
        public Optional<Long> createTaskNoteAddedNotification(TaskNotificationCommand command) {
            throw new AssertionError("createTaskNoteAddedNotification should not be called");
        }

        @Override
        public Optional<Long> createTaskDueDateNotification(TaskDueDateNotificationCommand command) {
            throw new AssertionError("createTaskDueDateNotification should not be called");
        }

        @Override
        public Optional<Long> createTaskActionNotification(TaskActionNotificationCommand command) {
            throw new AssertionError("createTaskActionNotification should not be called");
        }

        @Override
        public Optional<Long> createCalendarEventAssignedNotification(CalendarEventNotificationCommand command) {
            throw new AssertionError("createCalendarEventAssignedNotification should not be called");
        }
    }

    @Test
    void healthStillReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void caseSearchFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/search").param("query", "smith"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message", containsString("Authentication is required")))
                .andExpect(jsonPath("$.path").value("/api/cases/search"));
    }

    @Test
    void caseDetailRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/cases/123"));
    }

    @Test
    void caseTasksRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/cases/123/tasks"));
    }

    @Test
    void caseUpdatesRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123/updates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/cases/123/updates"));
    }

    @Test
    void contactSearchFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/contacts/search").param("query", "ada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/contacts/search"));
    }

    @Test
    void unreadNotificationsFailClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/notifications/unread"));
    }
}
