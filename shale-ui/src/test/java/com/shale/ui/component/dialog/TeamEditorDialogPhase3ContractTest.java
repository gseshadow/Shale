package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TeamEditorDialogPhase3ContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path)).replace("\r\n", "\n");
    }

    @Test
    void editorRetainsStageOwnerModalityCanonicalTitleThemeAndDirtyCloseContract() throws Exception {
        String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");
        assertTrue(source.contains("stage.initOwner(Objects.requireNonNull(owner))"));
        assertTrue(source.contains("stage.initModality(Modality.APPLICATION_MODAL)"));
        assertTrue(source.contains("static final String TITLE = \"Edit Case Team\""));
        assertTrue(source.contains("createSecondaryWindowShell(stage, TITLE"));
        assertTrue(source.contains("ThemeManager.application().register(scene)"));
        assertTrue(source.contains("ThemeManager.application().unregister(scene)"));
        assertTrue(source.contains("stage.setOnCloseRequest"));
        assertTrue(source.contains("confirmDiscard()"));
    }

    @Test
    void twoOwnedBoundedRegionsStackAtNarrowWidthsAboveStableFooter() throws Exception {
        String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");
        assertTrue(source.contains("new SplitPane(searchRegion, assignedRegion)"));
        assertTrue(source.contains("Orientation.VERTICAL : Orientation.HORIZONTAL"));
        assertTrue(source.contains("results.setMinHeight(150)"));
        assertTrue(source.contains("results.setPrefHeight(220)"));
        assertTrue(source.contains("VBox.setVgrow(results, Priority.ALWAYS)"));
        assertTrue(source.contains("VBox.setVgrow(members, Priority.ALWAYS)"));
        assertTrue(source.indexOf("workspace, error, footer") > 0,
                "footer must remain outside both independently scrolling regions");
        assertFalse(source.contains("FlowPane(searchRegion"), "major panel ownership must not depend on wrapping");
    }

    @Test
    void candidatesUseSharedMiniUserCardsAndAccessibleSingleActivation() throws Exception {
        String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");
        assertTrue(source.contains("UserCardFactory.Variant.MINI"));
        assertTrue(source.contains("KeyCode.ENTER || event.getCode() == KeyCode.SPACE"));
        assertTrue(source.contains("Available users search results"));
        assertTrue(source.contains("No users match this search."));
        assertTrue(source.contains("No available users."));
        assertFalse(source.contains("results.setOnMouseClicked"),
                "the shared card must own pointer activation so one click cannot add twice");
    }

    @Test
    void saveUsesOneCompleteActorAwareCommandAndRejectsClosedOrDuplicateCompletion() throws Exception {
        String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");
        assertTrue(source.contains("saving.compareAndSet(false, true)"));
        assertTrue(source.contains("service.updateCaseTeam(new CaseTeamUpdateCommand"));
        assertTrue(source.contains("if (closing || !stage.isShowing()) return"));
        assertTrue(source.contains("setSaving(false)"));
    }

    @Test
    void a2TeamVocabularyIsScopedTokenDrivenAndImported() throws Exception {
        String css = read("shale-ui/src/main/resources/css/foundation/team-windows.css");
        String app = read("shale-ui/src/main/resources/css/app.css");
        assertTrue(app.contains("@import \"foundation/team-windows.css\""));
        assertTrue(css.contains(".team-window-root"));
        assertTrue(css.contains(".team-window-search-region"));
        assertTrue(css.contains(".team-window-assigned-region"));
        assertTrue(css.contains(".team-window-role-chip-inactive"));
        assertTrue(css.contains(".team-window-filtered-empty"));
        assertTrue(css.contains(".team-window-concurrency"));
        assertTrue(css.contains("-shale-color-"));
        assertFalse(css.matches("(?s).*#[0-9a-fA-F]{3,8}.*"), "team-window paint must use semantic tokens");
    }

    @Test
    void overviewStillReadsAuthoritativeMembershipsOnceAndShowsRolelessAndAllRoles() throws Exception {
        String source = read("shale-ui/src/main/java/com/shale/ui/controller/CaseController.java");
        assertTrue(source.contains("listCaseTeamMemberships"));
        assertTrue(source.contains("renderAuthoritativeTeam"));
        assertTrue(source.contains("No roles assigned"));
        assertTrue(source.contains("member.roles().stream()"));
    }
}
