package com.shale.server.mcp;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/** Internal, data-free observation. No route, token issuance, handler activation or cached authority. */
public final class McpCaseReadiness {
    private final McpCaseCompositionPolicy policy;
    private final ObjectProvider<McpAiCaseData> boundary;

    McpCaseReadiness(McpCaseCompositionPolicy policy, ObjectProvider<McpAiCaseData> boundary) {
        this.policy = policy;
        this.boundary = boundary;
    }

    /** Fresh bounded attestation on explicit internal inspection; never returns diagnostic details. */
    public String state() {
        if (!policy.boundaryRequired()) return "disabled";
        try {
            policy.requireBoundary();
            var data = boundary.getIfAvailable();
            if (data == null) return "not-ready";
            data.ready(System.nanoTime() + 10_000_000_000L);
            return "ready";
        } catch (RuntimeException unavailable) {
            return "not-ready";
        }
    }

    /** Deployment observation only. Every protected request independently reattests. */
    public void report() {
        LoggerFactory.getLogger(McpCaseReadiness.class).info("AI_CASE_SQL_READINESS {}", state());
    }
}
