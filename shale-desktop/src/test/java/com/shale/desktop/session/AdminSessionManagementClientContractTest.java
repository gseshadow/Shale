package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.shale.ui.services.UiRuntimeBridge;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

final class AdminSessionManagementClientContractTest {
    @Test void adapterUsesOnlyBoundedAuthoritativeAdminApiAndSafeProjection() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/desktop/session/AdminSessionManagementClient.java"));
        assertTrue(source.contains("/api/admin/sessions"));
        for(String query:new String[]{"page=","size=","activeOnly=","userId=","clientType=","since="})assertTrue(source.contains(query),query);
        assertTrue(source.contains("Authorization"));
        assertFalse(source.contains("UserSessions"));
        assertFalse(source.contains("currentAccessJti"));
    }

    @Test void listParsesServerDtoAndLogsOnlyStatusAndElapsedTime() throws Exception {
        String body="{\"items\":[{\"sessionId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"userId\":9,\"userDisplayName\":\"Sensitive Name\",\"userEmail\":\"sensitive@example.test\",\"clientType\":\"DESKTOP\",\"issuedAt\":\"2026-10-02T10:00:00Z\",\"expiresAt\":\"2026-10-02T11:00:00Z\",\"lastRefreshedAt\":null,\"revokedAt\":null,\"revocationReason\":null,\"currentSession\":true}],\"page\":0,\"size\":50}";
        try(Server server=new Server(200,body); LogCapture logs=new LogCapture()) {
            var result=client(server).list(filter());
            assertEquals(1,result.items().size());
            String output=logs.output();
            assertTrue(output.contains("status=200"));
            assertTrue(output.contains("elapsedMs="));
            assertFalse(output.contains("Sensitive Name"));
            assertFalse(output.contains("sensitive@example.test"));
            assertFalse(output.contains("header.payload.signature"));
        }
    }

    @Test void malformedListLogsOnlyParsingFailureClassWithoutResponseContent() throws Exception {
        try(Server server=new Server(200,"SENSITIVE_RESPONSE_BODY"); LogCapture logs=new LogCapture()) {
            assertThrows(AdminSessionManagementClient.SessionManagementException.class,()->client(server).list(filter()));
            String output=logs.output();
            assertTrue(output.contains("response parsing failed exceptionClass=JsonSyntaxException"));
            assertFalse(output.contains("SENSITIVE_RESPONSE_BODY"));
            assertFalse(output.contains("header.payload.signature"));
        }
    }

    private static AdminSessionManagementClient client(Server server) {
        DesktopServerSession session=new DesktopServerSession();
        session.install(new DesktopServerSession.Credential("header.payload.signature",UUID.randomUUID(),UUID.randomUUID(),Instant.now().plusSeconds(60)));
        return new AdminSessionManagementClient(server.endpoint(),HttpClient.newHttpClient(),session);
    }

    private static UiRuntimeBridge.AdminSessionFilter filter(){return new UiRuntimeBridge.AdminSessionFilter(null,null,false,null,0,50);}

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        Server(int status,String body) throws IOException {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/admin/sessions",exchange->respond(exchange,status,body));
            server.start();
        }
        URI endpoint(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/admin/sessions");}
        @Override public void close(){server.stop(0);}
        private static void respond(HttpExchange exchange,int status,String body) throws IOException {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger=(Logger)LoggerFactory.getLogger(AdminSessionManagementClient.class);
        private final ListAppender<ILoggingEvent> appender=new ListAppender<>();
        LogCapture(){appender.start();logger.addAppender(appender);}
        String output(){return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",(a,b)->a+"\n"+b);}
        @Override public void close(){logger.detachAppender(appender);appender.stop();}
    }
}
