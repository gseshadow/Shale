package com.shale.server.runtime;

import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

/** Test-only closed diagnostic vocabulary. Never retain or render a driver/assertion message or cause. */
final class AuthVerificationSupport {
    enum Phase { PREREQUISITES, SETUP, CREDENTIAL_IDENTITY, REMEMBERED_ROTATION, RUNTIME_ISOLATION, CLEANUP }
    enum Step {
        CONFIGURATION, RESOURCES, POOLS, CREDENTIALS, ISOLATION,
        SESSION_SEED, CREATE, CREATED_DEADLINE, WRONG_INSTALLATION, ROTATE, PRINCIPAL, DEADLINE, REPLAY, JTI_IDENTITY,
        REVOKE, REVOKED, DISABLED_SEED, DISABLED_REJECT, REMOVED_SEED, REMOVED_REJECT,
        NULL_TENANT_SEED, NULL_TENANT_REJECT, EXPIRED_SEED, EXPIRED_REJECT, CALLER_RESTORATION,
        POOL_CLOSE, MARKER_IDENTITY, DATABASE_DROP, AUTH_LOGIN_IDENTITY, AUTH_LOGIN_DROP,
        RUNTIME_LOGIN_IDENTITY, RUNTIME_LOGIN_DROP
    }
    enum Category { ASSERTION, GUARD, SQL_PERMISSION, SQL_CONSTRAINT, SQL_TIMEOUT, SQL_CONNECTION, SQL_OTHER, UNEXPECTED }
    record Failure(Phase phase, Step step, Category category) {
        String detail() { return token(phase)+" "+token(step)+" "+token(category); }
    }
    @FunctionalInterface interface Operation { void run() throws Exception; }
    private final PrintStream output;
    private Phase phase=Phase.PREREQUISITES;
    private Step step=Step.CONFIGURATION;
    private Failure primary, cleanup;

    AuthVerificationSupport(PrintStream output) { this.output=output; }
    void at(Phase phase, Step step) { this.phase=phase;this.step=step; }
    void step(Step step) { this.step=step; }
    Failure primaryFailure() { return primary; }
    Failure cleanupFailure() { return cleanup; }

    void verify(Operation body, Operation cleanupOperation) {
        try { body.run(); }
        catch(Throwable failure) { primary=report(failure); }
        finally {
            at(Phase.CLEANUP,Step.POOL_CLOSE);
            try { cleanupOperation.run(); }
            catch(Throwable failure) { cleanup=report(failure); }
        }
        if(primary!=null || cleanup!=null)
            throw new AssertionError("Auth SQL verification failed; primary="+(primary==null?"none":primary.detail())
                    +"; cleanup="+(cleanup==null?"none":cleanup.detail()));
    }
    private Failure report(Throwable failure) {
        var result=new Failure(phase,step,category(failure));
        output.println("AUTH_VERIFY FAIL "+token(phase));
        output.println("AUTH_VERIFY DETAIL "+result.detail());
        return result;
    }
    static Category category(Throwable failure) {
        // Bound traversal even for hostile/cyclic causes. Do not inspect message/SQL/nextException.
        Throwable current=failure;
        for(int depth=0;current!=null && depth<8;depth++,current=current.getCause()) {
            if(current instanceof SQLTimeoutException) return Category.SQL_TIMEOUT;
            if(current instanceof SQLException sql) {
                String state=sql.getSQLState();
                if(sql.getErrorCode()==229 || sql.getErrorCode()==230 || sql.getErrorCode()==916
                        || (state!=null && state.startsWith("28"))) return Category.SQL_PERMISSION;
                if(state!=null && state.startsWith("23")) return Category.SQL_CONSTRAINT;
                if(state!=null && state.startsWith("08")) return Category.SQL_CONNECTION;
                if(state!=null && state.startsWith("HYT")) return Category.SQL_TIMEOUT;
                return Category.SQL_OTHER;
            }
        }
        if(failure instanceof AssertionError) return Category.ASSERTION;
        if(failure instanceof IllegalStateException || failure instanceof IllegalArgumentException) return Category.GUARD;
        return Category.UNEXPECTED;
    }
    private static String token(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }

    static boolean sameSid(byte[] expected,byte[] actual) {
        return expected!=null && expected.length>0 && actual!=null && java.util.Arrays.equals(expected,actual);
    }
    static boolean sameUuid(UUID expected, String actual) {
        // UUID.fromString alone also accepts abbreviated non-canonical groups.
        return expected!=null && actual!=null
                && actual.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                && expected.equals(UUID.fromString(actual));
    }
    static boolean exactlyOneUuid(ResultSet rows, UUID expected) throws SQLException {
        return rows.next() && sameUuid(expected,rows.getString(1)) && !rows.next();
    }
}
