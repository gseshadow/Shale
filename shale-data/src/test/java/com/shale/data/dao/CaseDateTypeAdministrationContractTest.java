package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

final class CaseDateTypeAdministrationContractTest {
    private static final String DAO = read("src/main/java/com/shale/data/dao/CaseDateDao.java");
    private static final String PORT = read("../shale-core/src/main/java/com/shale/core/service/CaseServicePort.java");
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @Test void backendExposesAdministrationApiAndKeepsSelectorSeparate(){
        assertTrue(PORT.contains("listCaseDateTypesForAdministration"));
        assertTrue(PORT.contains("CaseDateTypeCommand"));
        assertTrue(DAO.contains("listEffectiveCaseDateTypes"));
        assertTrue(DAO.contains("listCaseDateTypesForAdministration"));
        assertTrue(DAO.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
    }
    @Test void mutationsUseTenantOwnedOverlayRowsAndRowVersion(){
        assertTrue(DAO.contains("validateAdminActor"));
        assertTrue(DAO.contains("e.shaleClientId()==null"));
        assertFalse(DAO.contains("upsertOverride"));
        assertTrue(DAO.contains("ShaleClientId=? AND RowVer=?"));
        assertTrue(DAO.contains("UPDATE dbo.CaseDateTypes SET IsDeleted=1,IsActive=0"));
        assertFalse(DAO.contains("DELETE FROM dbo.CaseDateTypes"));
        assertFalse(DAO.contains("UPDATE dbo.CalendarEvents"));
        assertFalse(DAO.contains("INSERT dbo.CalendarEvents"));
        assertTrue(DAO.contains("requireCustomType(e)"));
        assertTrue(DAO.contains("ensureStableKeyUnchanged"));
        assertTrue(DAO.contains("System-defined Case Date Types are protected"));
        assertTrue(DAO.contains("softDeleteType(con,c.shaleClientId(),c.actorUserId(),e.id(),c.expectedRowVer())"));
    }
    @Test void validationCoversSchemaFields(){
        assertTrue(DAO.contains("DEADLINE"));
        assertTrue(DAO.contains("#[0-9A-Fa-f]{6}"));
        assertTrue(DAO.contains("supportsTime"));
        assertTrue(DAO.contains("SystemKey is invalid"));
    }
    @Test void customNamesAreNormalizedAndDuplicateChecked(){
        assertTrue(DAO.contains("LOWER(LTRIM(RTRIM(Name)))=LOWER(LTRIM(RTRIM(?)))"));
        assertTrue(DAO.contains("A Case Date Type with that name already exists."));
        assertTrue(DAO.contains("System keys are reserved for protected system-defined Case Date Types."));
    }
    @Test void everyAuthoritativeMutationAppendsOneSafeTransactionalAudit(){
        assertEquals(4, occurrences(DAO, "auditType(con,c.shaleClientId(),c.actorUserId()"));
        assertTrue(DAO.contains("EntityType.CASE_DATE_TYPE"));
        assertTrue(DAO.contains("MetadataKey.CASE_DATE_TYPE_ID,id"));
        assertTrue(DAO.contains("MetadataKey.ACTIVE,active"));
        assertTrue(DAO.contains("Action.CREATED"));
        assertTrue(DAO.contains("Action.UPDATED"));
        assertTrue(DAO.contains("Action.DEACTIVATED"));
        assertTrue(DAO.contains("Action.ACTIVATED"));
        assertTrue(DAO.contains("Action.DELETED"));
        assertTrue(DAO.contains("Action.RESTORED"));
        int tx=DAO.indexOf("private <T> T mutateAuditedType");
        assertTrue(DAO.indexOf("T r=op.run(con)",tx) < DAO.indexOf("con.commit();return r",tx));
        assertTrue(DAO.contains("catch(Exception e){con.rollback()"));
    }
    @Test void trustedIdentityConcurrencyAndFailureChecksPrecedeAudit(){
        assertTrue(DAO.contains("validateSessionActor(con,tenant,actor);validateActiveAdminActor"));
        assertTrue(DAO.contains("ISNULL(IsRemoved,0)=0 AND ISNULL(is_admin,0)=1"));
        assertTrue(DAO.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
        assertTrue(DAO.contains("ShaleClientId=? AND RowVer=?"));
        assertTrue(DAO.contains("if(ps.executeUpdate()!=1)throw new IllegalStateException(\"Case date type changed.\")"));
        int mutation=DAO.indexOf("EffectiveCaseDateTypeDto updated=updateTypeRow");
        int audit=DAO.indexOf("auditType(con,c.shaleClientId(),c.actorUserId(),updated.id()", mutation);
        assertTrue(mutation > 0 && audit > mutation);
        String auditBody=DAO.substring(DAO.indexOf("private void auditType"),DAO.indexOf("private EffectiveCaseDateTypeDto insertType"));
        assertFalse(auditBody.toLowerCase().matches("(?s).*(name|description|color|systemkey|rowver|case_id|case_date_id|note|starts_at|ends_at).*"));
    }
    private static int occurrences(String source,String token){int count=0,at=0;while((at=source.indexOf(token,at))>=0){count++;at+=token.length();}return count;}
}
