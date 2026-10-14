package com.shale.data.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Protects bounded capability and deployment scope; executable authority needs local SQL verification. */
class PreTenantAuthenticationModuleContractTest {
    private String sql()throws Exception{return Files.readString(Path.of("..","docs","sql","2026-10-14_pretenant_authentication_modules.sql"));}
    @Test void modulesConfineDboAuthorityWithoutGrantingCallerBypass()throws Exception{
        String sql=sql();
        assertEquals(2,sql.split("WITH EXECUTE AS OWNER",-1).length-1);
        assertEquals(2,sql.split("IF USER_NAME\\(\\) <> N'dbo'",-1).length-1);
        assertTrue(sql.contains("USER_NAME((SELECT principal_id FROM sys.schemas"),"deployment must validate inherited dbo ownership");
        String executable=sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "");
        assertFalse(executable.matches("(?is).*\\b(GRANT|DENY|REVOKE|ALTER|IMPERSONATE)\\b.*"),"unknown production grants and existing policy/module authority must remain untouched");
        assertFalse(executable.contains("sp_set_session_context"),"lookup must never stamp trusted context");
        assertFalse(executable.matches("(?is).*\\bEXEC(?:UTE)?\\s*\\(.*"),"module must use static parameterized SQL");
    }
    @Test void identityPopulationIsCapturedOnceAndBoundedBeforeLifecycleFiltering()throws Exception{
        String sql=sql();
        assertEquals(2,sql.split("TOP \\(2\\)",-1).length-1);
        assertTrue(sql.contains("COUNT_BIG(*) FROM #CredentialCandidates)=1"));
        assertTrue(sql.contains("COUNT_BIG(*) FROM #RememberCandidates)=1"));
        int capture = sql.indexOf("INTO #RememberCandidates");
        int output = sql.indexOf("SELECT r.ShaleClientId,r.UserId,r.SessionId,u.Email FROM #RememberCandidates");
        assertFalse(sql.substring(capture,output).contains("JOIN dbo.Users"),"raw credential ambiguity must be checked before joins can hide invalid owners");
        assertTrue(sql.contains("DATALENGTH(@Email) BETWEEN 1 AND 510"));
        assertTrue(sql.contains("DATALENGTH(@CredentialHash)=32"));
        assertTrue(sql.contains("Id>0 AND ShaleClientId>0"));
        assertTrue(sql.contains("t.Id=#CredentialCandidates.ShaleClientId"),"orphan tenant identities must fail closed");
        assertTrue(sql.contains("t.Id=r.ShaleClientId"));
        assertTrue(sql.contains("s.ShaleClientId=r.ShaleClientId AND s.UserId=r.UserId"));
        assertTrue(sql.contains("s.RevokedAt IS NULL AND s.ExpiresAt>@Now"));
        assertTrue(sql.contains("r.AbsoluteExpiresAt>SYSUTCDATETIME()"));
    }
}
