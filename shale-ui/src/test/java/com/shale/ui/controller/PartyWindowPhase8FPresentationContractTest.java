package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class PartyWindowPhase8FPresentationContractTest {
    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void addAndEditPartyDialogsOwnTheSharedA2WindowVocabulary() throws Exception {
        String add = read("src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java");
        String edit = read("src/main/java/com/shale/ui/controller/CaseController.java");
        String css = read("src/main/resources/css/foundation/party-windows.css");
        String appCss = read("src/main/resources/css/app.css");

        assertTrue(add.contains("party-window-shell"), "Add Party must opt into the focused party window shell");
        assertTrue(edit.contains("party-window-shell"), "Edit Party must share the same party window shell");
        assertTrue(add.contains("party-window-scroll"), "Add Party content must scroll independently of dialog actions");
        assertTrue(edit.contains("party-window-scroll"), "Edit Party content must scroll independently of dialog actions");
        assertTrue(add.contains("ScrollBarPolicy.NEVER"), "Add Party must prevent page-level horizontal scrolling");
        assertTrue(edit.contains("ScrollBarPolicy.NEVER"), "Edit Party must prevent page-level horizontal scrolling");
        assertTrue(add.contains("WindowSizingUtil.sizeModalStage"), "Add Party must size against its owner screen");
        assertTrue(edit.contains("WindowSizingUtil.sizeModalStage"), "Edit Party must size against its owner screen");
        assertTrue(appCss.contains("@import \"foundation/party-windows.css\";"), "The shared stylesheet must be loaded");
        for (String selector : new String[] {"party-window-root", "party-window-guidance", "party-window-results",
                "party-window-loading", "party-window-empty", "party-window-filtered-empty", "party-window-selected",
                "party-window-validation", "party-window-duplicate", "party-window-failure", "party-window-concurrency",
                "party-window-footer"}) {
            assertTrue(css.contains("." + selector), "Missing semantic party selector: " + selector);
        }
    }

    @Test
    void stylingKeepsCasePartyAuthoritySeparateFromRequestedFrom() throws Exception {
        String add = read("src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java");
        String requestedFrom = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        String caseController = read("src/main/java/com/shale/ui/controller/CaseController.java");

        assertTrue(caseController.contains("caseDao.addCaseParty("), "Add Party must retain the authoritative CaseParty mutation");
        assertFalse(add.contains("RequestedFromWorkflowDialog"), "Add Party must not delegate to Requested From");
        assertFalse(requestedFrom.contains("addCaseParty("), "Requested From selection must not create a CaseParty");
        assertTrue(add.contains("state.selectedEntity.id()"), "Selection must return its stable numeric entity identity");
        assertTrue(add.contains("state.selectedEntity.entityType()"), "Selection must retain the explicit entity-kind discriminator");
    }

    @Test
    void controlsExposeSemanticStylesAndAccessibleNamesWithoutInlinePaint() throws Exception {
        String add = read("src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java");

        assertTrue(add.contains("ControlStyles.apply"), "Party actions must use shared semantic button styling");
        assertTrue(add.contains("ControlStyles.formControl"), "Party selectors must use the shared form-control shell");
        assertTrue(add.contains("setAccessibleText(\"Party role, required\")"), "Required role must have an accessible name");
        assertTrue(add.contains("setAccessibleText(\"Matching party entities\")"), "Results must have an accessible identity");
        assertFalse(add.contains("setStyle("), "Party workflow must not own hard-coded page paint");
    }

	@Test
	void everyPartyWindowSelectorIsRootScopedAndCannotRestyleIntake() throws Exception {
		String css = read("src/main/resources/css/foundation/party-windows.css")
				.replaceAll("(?s)/\\*.*?\\*/", "");
		Matcher rules = Pattern.compile("(?s)([^{}]+)\\{").matcher(css);
		while (rules.find()) {
			for (String selector : rules.group(1).split(",")) {
				String normalized = selector.trim();
				assertTrue(normalized.startsWith(".party-window-root"),
						"Party CSS leaked an unscoped selector: " + normalized);
			}
		}
		for (String generic : new String[] {".dialog-pane", ".button", ".label", ".scroll-pane",
				".text-field", ".combo-box", ".check-box", ".form-"}) {
			assertFalse(Pattern.compile("(?m)^\\s*" + Pattern.quote(generic)).matcher(css).find(),
					"Party CSS must not start an unrelated generic selector: " + generic);
		}
	}
}
