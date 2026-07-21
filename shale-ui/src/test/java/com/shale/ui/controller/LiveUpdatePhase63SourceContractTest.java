package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class LiveUpdatePhase63SourceContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)).replace("\r\n", "\n"); }

    @Test
    void caseLinksPublishDomainAndAuditInvalidationsAfterSuccessfulMutationOnly() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CaseController.java");
        int action = source.indexOf("Object result = action.call();");
        int publish = source.indexOf("publishCaseLinkLiveInvalidations(operation", action);
        int catchBlock = source.indexOf("} catch (Exception ex)", publish);
        assertTrue(action >= 0 && publish > action, "Case Link invalidations must publish after mutation action returns.");
        assertTrue(catchBlock > publish, "Case Link invalidations must stay out of validation/conflict/failure catch path.");
        assertTrue(source.contains("runtimeBridge.publishCaseLinkChanged"));
        assertTrue(source.contains("runtimeBridge.publishEntityAuditActivityAdded"));
    }

    @Test
    void caseLinkShareChangesPublishIndependentRoutableEventsAfterMutationReturns() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CaseController.java");
        int mutation = source.indexOf("Object result = action.call();");
        int domainPublish = source.indexOf("publishCaseLinkLiveInvalidations(operation, activeCaseId", mutation);
        int catchBlock = source.indexOf("} catch (Exception ex)", mutation);
        assertTrue(domainPublish > mutation && domainPublish < catchBlock, "Share invalidations must be derived at the successful mutation boundary.");
        assertTrue(source.contains("shareLiveChangesForCreate(input.shareAdds())"));
        assertTrue(source.contains("shareLiveChangesForUpdate(input.shareAdds(), input.shareUpdates(), input.shareRemovals(), link.shares())"));
        assertTrue(source.contains("LiveUpdateEvents.CHANGE_ADDED"));
        assertTrue(source.contains("LiveUpdateEvents.CHANGE_UPDATED"));
        assertTrue(source.contains("LiveUpdateEvents.CHANGE_REMOVED"));
        assertTrue(source.contains("runtimeBridge.publishCaseLinkShareChanged(caseId, caseLinkId"));
    }

    @Test
    void subscribersFilterByTenantAndRouteByStableIdentifiers() throws Exception {
        String caseSource = read("src/main/java/com/shale/ui/controller/CaseController.java");
        String contactSource = read("src/main/java/com/shale/ui/controller/ContactViewController.java");
        String settingsSource = read("src/main/java/com/shale/ui/controller/SettingsController.java");
        String auditSource = read("src/main/java/com/shale/ui/controller/AuditLogViewerController.java");
        assertTrue(caseSource.contains("event.shaleClientId() != tenantId") && caseSource.contains("eventCaseId != caseId.longValue()"));
        assertTrue(contactSource.contains("event.shaleClientId() != tenantId") && contactSource.contains("eventContactId != contactId"));
        assertTrue(settingsSource.contains("event.shaleClientId() != tenantId") && settingsSource.contains("isAdminUser()"));
        assertTrue(auditSource.contains("event.shaleClientId() != tenantId") && auditSource.contains("selectedMode == ViewerMode.PHI_AUDIT"));
    }

    @Test
    void auditViewerReloadsEntityActivityAndAllButNotPhiOnly() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/AuditLogViewerController.java");
        assertTrue(source.contains("LiveUpdateEvents.ENTITY_AUDIT_ACTIVITY"));
        assertTrue(source.contains("if (selectedMode == ViewerMode.PHI_AUDIT) return;"));
        assertTrue(source.contains("Platform.runLater(this::loadAuditRows)"));
    }
}
