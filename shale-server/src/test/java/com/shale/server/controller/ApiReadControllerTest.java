package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.CalendarEventNotificationCommand;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.NotificationServicePort.TaskActionNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskDueDateNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskNotificationCommand;
import com.shale.core.service.TaskServicePort;
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
                .andExpect(jsonPath("$.caseNumber").value("CASE-501"));

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

        @Override
        public Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId) {
            this.detailCaseId = caseId;
            this.detailShaleClientId = shaleClientId;
            return Optional.of(new CaseDetailDto(caseId, "CASE-501", "Smith v. Example", "Detail", "Open", 10,
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
            return List.of(caseOverview());
        }

        @Override
        public List<com.shale.core.dto.CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
            throw new AssertionError("listCaseUpdates should not be called");
        }

        @Override
        public List<com.shale.core.dto.CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
            throw new AssertionError("listCaseStatuses should not be called");
        }

        @Override
        public List<com.shale.core.dto.PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
            throw new AssertionError("listPracticeAreas should not be called");
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
            throw new AssertionError("addCaseNote should not be called");
        }

        @Override
        public CaseDetailDto updateCaseCoreDetails(UpdateCaseCoreDetailsCommand command) {
            throw new AssertionError("updateCaseCoreDetails should not be called");
        }

        @Override
        public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
            throw new AssertionError("reorderCaseStatuses should not be called");
        }

        private static CaseOverviewDto caseOverview() {
            return new CaseOverviewDto(501L, "CASE-501", "Smith v. Example", "Open", 1, "#00AA00",
                    31, "Ada Attorney", "#111111", 10, "PI", "#222222",
                    null, null, null, null, null, null, "Caller", "Client", List.of(),
                    "Opposing", List.of("Ada Attorney"), "Overview");
        }
    }

    private static final class RecordingTaskServicePort implements TaskServicePort {
        private long caseId;
        private int shaleClientId;

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
            throw new AssertionError("listAssignedTasks should not be called");
        }

        @Override
        public Optional<com.shale.core.dto.TaskDetailDto> getTaskDetail(long taskId, int shaleClientId) {
            throw new AssertionError("getTaskDetail should not be called");
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
            throw new AssertionError("createTaskWithDefaultStatus should not be called");
        }

        @Override
        public void updateTask(UpdateTaskCommand command) {
            throw new AssertionError("updateTask should not be called");
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

        @Override
        public List<ContactSummary> searchContacts(int shaleClientId, String query, int limit) {
            this.shaleClientId = shaleClientId;
            this.query = query;
            this.limit = limit;
            return List.of(new ContactSummary(801, "Ada Lovelace", "ada@example.test", "555-0100"));
        }

        @Override
        public Optional<ContactDetail> getContactDetail(int contactId, int shaleClientId) {
            throw new AssertionError("getContactDetail should not be called");
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
