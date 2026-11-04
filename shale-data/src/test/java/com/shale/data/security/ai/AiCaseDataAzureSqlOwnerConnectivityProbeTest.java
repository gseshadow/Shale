package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.sql.SQLException;

/** Owner-operated, non-mutating login/catalog probe. No schema import, objects or acceptance receipt. */
class AiCaseDataAzureSqlOwnerConnectivityProbeTest {
    @Test @Timeout(180) void connectAndInspectFreshOwnedDatabaseWithoutProvisioning()throws Exception {
        Assumptions.assumeTrue("PROBE_OWNED_DISPOSABLE_AZURE_LOGIN".equals(System.getenv("SHALE_AZURE_OWNER_PROBE_OPT_IN")),
                "Separate non-mutating owner probe opt-in required; not native acceptance");
        AiCaseDataAzureSqlDisposableSetupTest.requireQuietDriverLogging();
        String db=AiCaseDataAzureSqlDisposableSetupTest.validateOwner(System.getenv());
        System.err.println("AZURE_OWNER_PROBE status=BEGIN");
        try(var c=AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(AiCaseDataAzureSqlDisposableSetupTest.ownerSource(System.getenv()))) {
            AiCaseDataAzureSqlDisposableSetupTest.phase(c,AiCaseDataAzureSqlDisposableSetupTest.Phase.OWNER_PREFLIGHT,
                    ()->AiCaseDataAzureSqlDisposableSetupTest.ownerPreflight(c,db));
        } catch(SQLException|AssertionError failure) {
            // Prevent raw target, principal or SQL exception text reaching Surefire.
            throw new IllegalStateException("Owner probe failed; exceptionClass="+failure.getClass().getName()+"; discard owned scope; no setup");
        }
        System.err.println("AZURE_OWNER_PROBE status=PASS applicationObjectsCreated=false nativeAcceptance=false");
    }
}
