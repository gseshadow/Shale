package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class MaterialRequestUpdatesContractTest {
    private static String dao() throws Exception { return Files.readString(Path.of("src/main/java/com/shale/data/dao/MaterialRequestDao.java")); }
    private static String migration() throws Exception { return Files.readString(Path.of("../docs/sql/2026-07-29_material_request_updates.sql")); }

    @Test void migrationIsIdempotentTenantScopedAndAppendOnly() throws Exception {
        String s=migration();
        assertTrue(s.contains("OBJECT_ID(N'dbo.MaterialRequestUpdates', N'U') IS NULL"));
        assertTrue(s.contains("IX_MaterialRequestUpdates_Request_Chronology"));
        assertTrue(s.contains("FK_MaterialRequestUpdates_Request_Tenant_Case"));
        assertTrue(s.contains("FK_MaterialRequestUpdates_ActorUserId_Users"));
        assertTrue(s.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequestUpdates"));
        assertTrue(s.contains("USER_NOTE','SYSTEM_CHANGE','SYSTEM_EVENT"));
        assertFalse(s.matches("(?s).*UPDATE\\s+dbo\\.MaterialRequestUpdates.*"));
        assertFalse(s.matches("(?s).*DELETE\\s+FROM\\s+dbo\\.MaterialRequestUpdates.*"));
        assertFalse(s.contains("INSERT dbo.MaterialRequestUpdates SELECT"),"No historical backfill");
        assertFalse(s.contains("DROP CONSTRAINT CK_MaterialRequests_Closure"));
        assertFalse(s.contains("DROP CONSTRAINT CK_MaterialRequests_FollowUpIntervalDays"));
    }

    @Test void notesAndChangesShareAtomicAppendOnlyBoundary() throws Exception {
        String s=dao();
        assertTrue(s.contains("public MaterialRequestUpdateDto addNote"));
        assertTrue(s.contains("body.length()>4000"));
        assertTrue(s.contains("phi.auditCreate(con,c.actorUserId(),\"MaterialRequestUpdates\",\"Body\""));
        assertTrue(s.contains("touchCase(con,c.caseId(),c.shaleClientId())"));
        assertTrue(s.contains("appendChangeUpdates(con,prior,c,status,closure,next,mutationTime)"));
        assertTrue(s.contains("Material request created."));
        assertTrue(s.contains("Description updated."));
        assertTrue(s.contains("Request reopened."));
        assertTrue(s.contains("ORDER BY mu.CreatedAt DESC,mu.Id DESC"));
        assertFalse(s.contains("updateMaterialRequestUpdate"));
        assertFalse(s.contains("deleteMaterialRequestUpdate"));
    }

    @Test void schedulerAndNotificationPathsNeverAppendHistory() throws Exception {
        String s=dao();
        int due=s.indexOf("listDueNotificationCandidates"), updates=s.indexOf("public List<MaterialRequestUpdateDto> listUpdates");
        assertFalse(s.substring(due,updates).contains("appendUpdate("));
    }

    @Test void nullableTrackedChangesAreComparedAndRenderedSafely() throws Exception {
        String s=dao();
        assertTrue(s.contains("Objects.equals(p.requestedByUserId(),c.requestedByUserId())"));
        assertTrue(s.contains("Objects.equals(p.assignedToUserId(),c.assignedToUserId())"));
        assertTrue(s.contains("Objects.equals(requestedFromKey(p),requestedFromKey(c))"));
        assertTrue(s.contains("if(v==null)return \"None\""));
        assertTrue(s.contains("if(id==null)return null"), "Null lookup IDs must never be queried");
        assertFalse(s.contains("Set.of(\"closed\",\"cancelled\").contains(oldKey)"));
        assertFalse(s.contains("Set.of(\"closed\",\"cancelled\").contains(newKey)"));
    }

    @Test void updateHistoryAndAuditsShareTheOptimisticTransaction() throws Exception {
        String s=dao();
        int start=s.indexOf("public MaterialRequestDetailDto update(UpdateMaterialRequestCommand c)");
        int end=s.indexOf("public void softDelete",start);
        String update=s.substring(start,end);
        assertTrue(update.contains("con.setAutoCommit(false)"));
        assertTrue(update.indexOf("findForUpdate(con,c)") < update.indexOf("updateRow(con,c"));
        assertTrue(update.contains("if(rows==0)throw new IllegalStateException(\"Material request has changed. Please reload and try again.\")"));
        assertTrue(update.indexOf("appendChangeUpdates(") > update.indexOf("if(rows==0)"), "A stale write must not append history");
        assertTrue(update.indexOf("touchCase(") > update.indexOf("appendChangeUpdates("));
        assertTrue(update.indexOf("audit(") > update.indexOf("touchCase("));
        assertTrue(update.indexOf("con.commit()") > update.indexOf("audit("));
        assertTrue(update.contains("catch(Exception ex){rollback(con);throw ex;}"), "History failures must roll back the edit");
        assertTrue(update.contains("return findMaterialRequest("), "The save must return a refreshed RowVer");
    }
}
