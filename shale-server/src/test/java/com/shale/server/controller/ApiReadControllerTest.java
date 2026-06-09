package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.CalendarEventNotificationCommand;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.NotificationServicePort.TaskActionNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskDueDateNotificationCommand;
import com.shale.core.service.NotificationServicePort.TaskNotificationCommand;
import com.shale.core.service.TaskServicePort;
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.ServerRuntimeSessionState;

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

    @Test
    void unreadNotificationsReachesServiceLayerWithDevelopmentHeaders() throws Exception {
        RecordingNotificationServicePort notificationServicePort = new RecordingNotificationServicePort();
        ApiReadController apiReadController = new ApiReadController(
                unusedPort(CaseServicePort.class),
                unusedPort(TaskServicePort.class),
                unusedPort(ContactServicePort.class),
                notificationServicePort,
                new ServerRuntimeSessionState(new DevelopmentHeaderServerSessionResolver(), currentRequestProvider()));
        MockMvc devMockMvc = MockMvcBuilders
                .standaloneSetup(apiReadController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

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
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("not_implemented"))
                .andExpect(jsonPath("$.message", containsString("TODO: server auth/session context is not wired yet")))
                .andExpect(jsonPath("$.path").value("/api/cases/search"));
    }

    @Test
    void caseDetailRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/cases/123"));
    }

    @Test
    void caseTasksRouteFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/cases/123/tasks"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/cases/123/tasks"));
    }

    @Test
    void contactSearchFailsClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/contacts/search").param("query", "ada"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/contacts/search"));
    }

    @Test
    void unreadNotificationsFailClosedUntilServerSessionContextExists() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.path").value("/api/notifications/unread"));
    }
}
