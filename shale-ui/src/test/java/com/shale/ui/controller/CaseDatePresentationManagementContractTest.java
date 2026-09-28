package com.shale.ui.controller;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Protects the administrator-visible behavior of both independent presentation lists. */
final class CaseDatePresentationManagementContractTest {
    private static String read(String file) {
        try { return Files.readString(Path.of(file)).replace("\r\n", "\n"); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }

    @Test void settingsEditorNamesBothIndependentListsAndExplainsOverviewPrecedenceAndIntake() {
        String source=read("src/main/java/com/shale/ui/controller/CaseDatePresentationManagementPane.java");
        assertAll(
                () -> assertTrue(source.contains("Case Card dates"), "card defaults need their own named editor"),
                () -> assertTrue(source.contains("Default Case Overview dates"), "Overview defaults need their own named editor"),
                () -> assertTrue(source.contains("only to cases without a per-case Overview override"), "per-case precedence must be visible"),
                () -> assertTrue(source.contains("Intake may be hidden here without changing its protected workflow identity"), "presentation must not imply workflow retirement"));
    }

    @Test void activeEffectiveCandidatesUseStableIdentityAndHistoricalSavedRowsRemainVisible() {
        var global=type(1,null,"INTAKE",true,false);
        var custom=type(2,7,null,true,false);
        var inactive=type(3,7,null,false,false);
        assertEquals(List.of(global,custom), CaseDatePresentationManagementPane.eligibleTypes(List.of(global,custom,inactive)));
        assertEquals("SYSTEM:intake",CaseDatePresentationManagementPane.identity(global));
        assertEquals("TYPE:2",CaseDatePresentationManagementPane.identity(custom));
        String source=read("src/main/java/com/shale/ui/controller/CaseDatePresentationManagementPane.java");
        assertTrue(source.contains("historical — no longer active"), "saved inactive definitions must render rather than disappear");
    }

    @Test void orderingExplicitEmptyDirtyStateAndStaleRecoveryAreFirstClass() {
        String source=read("src/main/java/com/shale/ui/controller/CaseDatePresentationManagementPane.java");
        assertAll(
                () -> assertTrue(source.contains("working.remove(selected)")),
                () -> assertTrue(source.contains("working.add(target,row)"), "move actions must change persisted order"),
                () -> assertTrue(source.contains("Saving will explicitly show no dates."), "empty must be intentional and saveable"),
                () -> assertTrue(source.contains("!identities().equals"), "closing/reloading must recognize unsaved changes"),
                () -> assertTrue(source.contains("This configuration changed elsewhere. Select Reload"), "stale RowVer needs actionable recovery"),
                () -> assertTrue(source.contains("replaceCaseDatePresentationConfiguration(command)"), "the UI must use the service port rather than SQL"));
    }

    @Test void successfulSavePublishesTenantInvalidationForCardsAndUncustomizedOverview() {
        String pane=read("src/main/java/com/shale/ui/controller/CaseDatePresentationManagementPane.java");
        String settings=read("src/main/java/com/shale/ui/controller/SettingsController.java");
        String cases=read("src/main/java/com/shale/ui/controller/CasesController.java");
        String detail=read("src/main/java/com/shale/ui/controller/CaseController.java");
        String myShale=read("src/main/java/com/shale/ui/controller/MyShaleController.java");
        String search=read("src/main/java/com/shale/ui/controller/SearchController.java");
        String contact=read("src/main/java/com/shale/ui/controller/ContactViewController.java");
        String organization=read("src/main/java/com/shale/ui/controller/OrganizationController.java");
        String user=read("src/main/java/com/shale/ui/controller/UserController.java");
        assertAll(
                () -> assertTrue(pane.indexOf("replaceCaseDatePresentationConfiguration(command)") < pane.indexOf("publisher.accept(editor.purpose)"), "publish only after commit returns"),
                () -> assertTrue(settings.contains("publishCaseDatePresentationChanged")),
                () -> assertTrue(cases.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(cases.contains("CASE_CARD.name()")),
                () -> assertTrue(detail.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(detail.contains("!overviewDateConfiguration.customized()"), "per-case overrides must not refresh as inherited defaults"),
                () -> assertTrue(myShale.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(search.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(contact.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(organization.contains("ENTITY_CASE_DATE_PRESENTATION")),
                () -> assertTrue(user.contains("ENTITY_CASE_DATE_PRESENTATION")));
    }

    private static EffectiveCaseDateTypeDto type(int id,Integer tenant,String key,boolean active,boolean deleted) {
        return new EffectiveCaseDateTypeDto(id,tenant,key,"Type "+id,null,"OTHER","#123456",false,id,active,deleted,
                tenant==null?EffectiveCaseDateTypeDto.Origin.GLOBAL:EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{1});
    }
}
