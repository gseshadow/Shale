package com.shale.server.diagnostics;

import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Bounded operational metadata from this server JVM; independent of AI composition. */
@Component
public final class JvmStartupDiagnostic implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(JvmStartupDiagnostic.class);
    private final AtomicBoolean emitted = new AtomicBoolean();

    @Override
    public void run(ApplicationArguments ignored) {
        if (!emitted.compareAndSet(false, true)) {
            return;
        }
        // Observability must not become an ordinary startup dependency. Never log exceptions.
        try {
            LOG.info(format(ZoneId.systemDefault().getId(), Runtime.version().toString(), replicaId()));
        } catch (RuntimeException ignoredFailure) {
            // Missing diagnostic evidence is a readiness blocker, not an application outage.
        }
    }

    private static String replicaId() {
        try {
            // The sole environment read: Azure's nonsecret worker identifier, never a config dump.
            return System.getenv("WEBSITE_INSTANCE_ID");
        } catch (SecurityException denied) {
            return null;
        }
    }

    static String format(String zoneId, String javaVersion, String replicaId) {
        return "SHALE_JVM_STARTUP zoneId=" + field(zoneId, "[A-Za-z0-9_+./:-]{1,128}")
                + " javaVersion=" + field(javaVersion, "[A-Za-z0-9_.+-]{1,128}")
                + " replicaId=" + field(replicaId, "[A-Za-z0-9_-]{1,128}");
    }

    private static String field(String value, String allowed) {
        return value != null && value.length() <= 128 && value.matches(allowed) ? value : "unavailable";
    }
}
