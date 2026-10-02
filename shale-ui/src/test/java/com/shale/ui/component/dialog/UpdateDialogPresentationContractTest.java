package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UpdateDialogPresentationContractTest {
	private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

	private static String read(String path) throws Exception {
		return Files.readString(ROOT.resolve(path)).replace("\r\n", "\n");
	}

	@Test
	void promptUsesSharedSemanticControlsAndContainsNoInlineStyling() throws Exception {
		String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/UpdateDialog.java");
		assertTrue(source.contains("ActionButtonFactory.semantic"),
				"update actions must use the shared semantic button factory");
		assertTrue(source.contains("ControlStyles.Purpose.PRIMARY"));
		assertTrue(source.contains("ControlStyles.Purpose.SECONDARY"));
		assertTrue(source.contains("primary.setDefaultButton(true)"));
		assertTrue(source.contains("secondary.setCancelButton(true)"));
		assertFalse(source.contains("setStyle("), "update presentation must not own inline CSS");
	}

	@Test
	void optionalAndMandatoryStatesKeepTheirAllowedSecondaryActions() throws Exception {
		String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/UpdateDialog.java");
		assertTrue(source.contains("mandatory ? \"Exit application\" : \"Not now\""));
		assertTrue(source.contains("mandatory ? \"REQUIRED UPDATE\" : \"UPDATE AVAILABLE\""));
		assertTrue(source.contains("mandatory ? \"update-dialog-mandatory\" : \"update-dialog-optional\""));
		assertTrue(source.contains("mandatory ? \"shale-semantic-chip-warning\" : \"shale-semantic-chip-info\""));
	}

	@Test
	void detailsAreBoundedAndScrollableWithoutDestabilizingActions() throws Exception {
		String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/UpdateDialog.java");
		assertTrue(source.contains("detailsScroll.setFitToWidth(true)"));
		assertTrue(source.contains("detailsScroll.setMaxHeight(180)"));
		assertTrue(source.contains("VBox.setVgrow(detailsScroll, Priority.ALWAYS)"),
				"bounded windows must shrink the scrolling details rather than the action footer");
		assertTrue(source.indexOf("detailsScroll, actions") > 0,
				"actions must remain outside the independently scrolling detail region");
		assertTrue(source.contains("DialogSizingUtil.applyConfirmationDialogSizing"));
		String sizing = read("shale-ui/src/main/java/com/shale/ui/util/DialogSizingUtil.java");
		assertTrue(sizing.indexOf("stage.sizeToScene()") < sizing.lastIndexOf("WindowSizingUtil.sizeModalStage"),
				"content measurement must happen before the final screen-bounded stage size");
	}

	@Test
	void closeRequestsUseTheConfiguredDeclineActionAndARealOwner() throws Exception {
		String source = read("shale-ui/src/main/java/com/shale/ui/component/dialog/UpdateDialog.java");
		assertTrue(source.contains("owner = resolveOwner(owner)"),
				"a null caller must still produce a window-modal prompt over the active application window");
		assertTrue(source.contains("stage.setOnCloseRequest"));
		assertTrue(source.contains("event.consume();\n\t\t\tdecline.run();"),
				"window dismissal must have the same mandatory Exit / optional Not-now semantics");
	}

	@Test
	void updateVocabularyIsTokenDrivenAndAvailableToBothThemes() throws Exception {
		String css = read("shale-ui/src/main/resources/css/foundation/utility-dialogs.css");
		String app = read("shale-ui/src/main/resources/css/app.css");
		String light = read("shale-ui/src/main/resources/css/theme/light.css");
		String dark = read("shale-ui/src/main/resources/css/theme/dark.css");
		assertTrue(app.contains("@import \"foundation/utility-dialogs.css\""));
		for (String selector : new String[] { ".update-dialog", ".update-dialog-status",
				".update-dialog-version-card", ".update-dialog-details-scroll", ".update-dialog-actions" }) {
			assertTrue(css.contains(selector), "missing update dialog selector " + selector);
		}
		assertTrue(css.contains("-shale-color-overlay-dialog"));
		assertTrue(light.contains("-shale-color-overlay-dialog:"));
		assertTrue(dark.contains("-shale-color-overlay-dialog:"));
		assertFalse(css.matches("(?s).*#[0-9a-fA-F]{3,8}.*"),
				"utility dialog styling must rely on theme tokens rather than hard-coded colors");
	}
}
