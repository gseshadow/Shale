package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real loopback HTTP client/lifecycle, synthetic server and protected store.
 * Prevents Retry losing a saved sign-in or falling back to password/legacy SQL.
 * Does not certify Windows DPAPI, deployed API SQL, or installed-artifact acceptance. */
class DesktopRememberedRecoveryTest {
    @Test void retryRecoversAnOutageBeforeRotation() throws Exception { retry(false); }
    @Test void retryRecoversAResponseLostAfterRotation() throws Exception { retry(true); }

    private void retry(boolean rotateBeforeFailure) throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rotateBeforeFailure = rotateBeforeFailure;
            var failure = assertThrows(DesktopSessionEnrollmentClient.EnrollmentException.class,
                    fixture.lifecycle::restore);
            assertEquals(DesktopSessionEnrollmentClient.Failure.TRANSIENT, failure.failure());
            assertTrue(fixture.store.value.startsWith("v1\n"), "outage must retain the recovery journal");
            assertTrue(fixture.lifecycle.session().current().isEmpty(), "failed restore must not install authority");
            var restored = fixture.lifecycle.restore();
            assertEquals(11, restored.shaleClientId());
            assertEquals(1, restored.userId());
            fixture.lifecycle.commitRemembered();
            assertEquals(fixture.accepted, fixture.store.value, "Retry must persist the server's current credential");
            assertEquals(rotateBeforeFailure ? 2 : 3, fixture.requests.get(),
                    "one Retry may use only the journal's bounded fallback after a security rejection");
            fixture.lifecycle.shutdown();
            assertTrue(fixture.lifecycle.session().current().isEmpty());
            assertEquals(fixture.accepted, fixture.store.value, "ordinary close must retain the rotated credential");
        }
    }

    @Test void authoritativeRestoreRejectionClearsSavedCredential() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.reject = true;
            var failure = assertThrows(DesktopSessionEnrollmentClient.EnrollmentException.class,
                    fixture.lifecycle::restore);
            assertEquals(DesktopSessionEnrollmentClient.Failure.SECURITY_REJECTED, failure.failure());
            assertNull(fixture.store.value, "revoked/disabled/replayed HTTP rejection must require password login");
            assertTrue(fixture.lifecycle.session().current().isEmpty());
            assertEquals(1, fixture.requests.get(), "a plain rejected credential must not be replayed");
        }
    }

    @Test void unavailableProtectedReadFailsBeforeAnyHttpRequest() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.store.readFailure = true;
            assertThrows(IllegalStateException.class, fixture.lifecycle::restore);
            assertNull(fixture.store.value, "unreadable protected state must be removed");
            assertEquals(0, fixture.requests.get());
            assertTrue(fixture.lifecycle.session().current().isEmpty());
        }
    }

    @Test void unavailableProtectedWriteFailsBeforeAnyHttpRequest() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.store.writeFailure = true;
            assertThrows(IllegalStateException.class, fixture.lifecycle::restore);
            assertEquals(0, fixture.requests.get(), "never rotate before a recovery journal can be protected");
            assertTrue(fixture.lifecycle.session().current().isEmpty());
        }
    }

    @Test void uncheckedPasswordLoginRemovesSavedStartupAuthority() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.lifecycle.stage(11, 1, "synthetic@test", "synthetic-password", false);
            fixture.lifecycle.shutdown();
            assertFalse(fixture.lifecycle.hasRememberedCredential());
            assertNull(fixture.lifecycle.restore(), "unchecked startup must not silently restore a previous user");
            assertEquals(0, fixture.requests.get());
        }
    }

    private static final class Store extends DesktopSessionEnrollmentLifecycleTest.Store {
        boolean readFailure, writeFailure;
        @Override public java.util.Optional<String> read() {
            if (readFailure) throw new IllegalStateException("synthetic protected-read failure");
            return super.read();
        }
        @Override public void write(String value) {
            if (writeFailure) throw new IllegalStateException("synthetic protected-write failure");
            super.write(value);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final Store store = new Store();
        final AtomicInteger requests = new AtomicInteger();
        final DesktopSessionEnrollmentLifecycle lifecycle;
        volatile String accepted = "synthetic-initial-credential";
        volatile boolean rotateBeforeFailure, reject;
        Fixture() throws Exception {
            store.value = accepted;
            server.createContext("/api/auth/desktop-session/restore", exchange -> {
                int attempt = requests.incrementAndGet();
                var request = new Gson().fromJson(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8), JsonObject.class);
                boolean valid = accepted.equals(request.get("credential").getAsString());
                int status = reject || !valid ? 401 : attempt == 1 ? 503 : 200;
                if (valid && !reject && (status == 200 || rotateBeforeFailure))
                    accepted = request.get("replacementCredential").getAsString();
                byte[] body = ("{\"accessToken\":\"synthetic-bearer\",\"sessionId\":\"" + UUID.randomUUID()
                        + "\",\"currentJti\":\"" + UUID.randomUUID() + "\",\"expiresAt\":\""
                        + Instant.now().plusSeconds(3600) + "\",\"rememberCredential\":\"" + accepted
                        + "\",\"userId\":1,\"shaleClientId\":11,\"email\":\"synthetic@test\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
                exchange.close();
            });
            server.start();
            lifecycle = new DesktopSessionEnrollmentLifecycle(new DesktopSessionEnrollmentClient(
                    "http://127.0.0.1:" + server.getAddress().getPort()), new DesktopServerSession(), store, UUID.randomUUID());
        }
        @Override public void close() { server.stop(0); }
    }
}
