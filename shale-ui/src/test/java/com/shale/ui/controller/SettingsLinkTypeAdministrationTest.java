package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;

final class SettingsLinkTypeAdministrationTest {
	@Test
	void overlayRowsClassifyAndSortAdministrationCards() {
		List<SettingsController.LinkTypeViewRow> rows = SettingsController.buildLinkTypeRows(List.of(
				new LinkTypeDto(1, null, "Zulu Global", "#111111", true, false, "shared", new byte[] {1}),
				new LinkTypeDto(2, 7, "Alpha Override", "#222222", true, false, "shared", new byte[] {2}),
				new LinkTypeDto(3, null, "Reset Global", "#333333", true, false, "reset", new byte[] {3}),
				new LinkTypeDto(4, 7, "Deleted Reset", "#444444", false, true, "reset", new byte[] {4}),
				new LinkTypeDto(5, 7, "Custom", "#555555", true, false, null, new byte[] {5}),
				new LinkTypeDto(6, 8, "Other Tenant", "#666666", true, false, null, new byte[] {6})), 7);

		assertEquals(List.of("Alpha Override", "Custom", "Reset Global"), rows.stream().map(SettingsController.LinkTypeViewRow::getName).toList());
		assertEquals(List.of("Tenant override", "Tenant custom", "Global/default"), rows.stream().map(SettingsController.LinkTypeViewRow::scopeLabel).toList());
	}

	@Test
	void linkTypeSettingsUseAdminOnlyAsyncAppStateAndRowVerPatterns() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
		String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));

		assertTrue(fxml.contains("fx:id=\"linkTypeAdministrationSection\""));
		assertTrue(containsCode(source, "linkTypeAdministrationSection.setVisible(visible)"));
		assertTrue(containsCode(source, "loadLinkTypesAsync(null);"));
		assertTrue(containsCode(source, "caseService.listLinkTypesForAdministration(tenantId, actorUserId)"));
		assertTrue(containsCode(source, "settingsLoadExecutor.submit"));
		assertTrue(containsCode(source, "Platform.runLater(() -> applyLinkTypeRows"));
		assertTrue(containsCode(source, "if (generation != linkTypeLoadGeneration) return;"));
		assertTrue(containsCode(source, "appState.getShaleClientId()"));
		assertTrue(containsCode(source, "appState.getUserId()"));
		assertTrue(containsCode(source, "selected.rowVer()"));
	}

	@Test
	void seededHashColorsAndStoredColorsConvertForPicker() {
		assertEquals(0x25 / 255.0, SettingsController.dbColorToFx("#2563EB").getRed(), 0.0001);
		assertEquals(0x63 / 255.0, SettingsController.dbColorToFx("#2563EB").getGreen(), 0.0001);
		assertEquals(0xEB / 255.0, SettingsController.dbColorToFx("#2563EB").getBlue(), 0.0001);
		assertEquals(0x28 / 255.0, SettingsController.dbColorToFx("0x28A745FF").getRed(), 0.0001);
	}

    private static boolean containsCode(String source, String expected) {
        return source.replaceAll("\\s+", "").contains(expected.replaceAll("\\s+", ""));
    }
}
