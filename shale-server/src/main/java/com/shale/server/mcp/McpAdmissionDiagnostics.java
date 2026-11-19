package com.shale.server.mcp;

import com.shale.data.config.AcceptanceVerificationBudget;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/** Acceptance-only operational events. Never retain request values, tokens, SQL or exception text. */
final class McpAdmissionDiagnostics {
    private final boolean enabled;
    private final LongSupplier clock;
    private static final Pattern VERIFIER = Pattern.compile("^(?:AI Case datasource attestation failed closed: |CIMD verification failed closed; stage=)([A-Z_]{1,40})(?: \\(SQL (-?[0-9]{1,9})\\)|; sqlCode=(-?[0-9]{1,9}))?");
    McpAdmissionDiagnostics(LongSupplier clock) {
        // This existing budget is active only after validating the fixed acceptance app, origin,
        // profile, database and every dedicated JDBC destination. Ordinary builds produce no events.
        this(AcceptanceVerificationBudget.operationMillis(10000) == 600000, clock);
    }
    McpAdmissionDiagnostics(boolean enabled, LongSupplier clock) { this.enabled=enabled; this.clock=clock; }
    Attempt begin(String method) { return new Attempt(method); }
    final class Attempt {
        private final String method;
        private final long started;
        Attempt(String value) {
            method=value==null?"NO_METHOD":switch(value) {
                case "server/discover" -> "DISCOVER"; case "tools/list" -> "TOOLS_LIST";
                case "tools/call" -> "TOOLS_CALL"; default -> "OTHER";
            };
            started=enabled?clock.getAsLong():0;
            if(enabled)event("START","NONE","NONE",0);
        }
        void accepted() { if(enabled)event("ACCEPTED","NONE","NONE",0); }
        void rejected(Throwable failure) {
            if(!enabled)return;
            String reason=failure instanceof SecurityException?"SECURITY":"UNAVAILABLE",stage="NONE";
            int sqlCode=0;Throwable cause=failure;
            for(int depth=0;cause!=null&&depth<8;depth++,cause=cause.getCause()) {
                if(cause instanceof SQLException sql) {sqlCode=sql.getErrorCode();reason=cause instanceof SQLTimeoutException?"SQL_TIMEOUT":"SQL_REJECTED";break;}
            }
            // Verifiers already discard driver text and expose closed metadata. Only recognize
            // that format when a corresponding verifier appears on the original exception stack.
            boolean verifier=false;
            for(var frame:failure.getStackTrace()) {
                verifier |= frame.getClassName().equals("com.shale.data.security.ai.AiCaseDataAttestation") && frame.getMethodName().equals("verify");
                verifier |= frame.getClassName().equals("com.shale.data.security.ai.AiCimdCaseData") && frame.getMethodName().equals("ready");
            }
            String message=failure.getMessage();
            if(verifier&&message!=null&&message.length()<=512) {
                var match=VERIFIER.matcher(message);
                if(match.find()) {
                    stage=match.group(1);reason="VERIFIER";
                    if(match.group(2)!=null)sqlCode=Integer.parseInt(match.group(2));
                    if(match.group(3)!=null)sqlCode=Integer.parseInt(match.group(3));
                }
            }
            event("REJECTED",reason,stage,sqlCode);
        }
        private void event(String result,String reason,String stage,int sqlCode) {
            long millis=Math.min(600000L,Math.max(0L,(clock.getAsLong()-started)/1000000L));
            LoggerFactory.getLogger(McpAdmissionDiagnostics.class).warn(
                "SHALE_MCP_ADMISSION result={} method={} elapsedMillis={} reason={} verifierStage={} sqlCode={}",
                result,method,millis,reason,stage,sqlCode);
        }
    }
}
