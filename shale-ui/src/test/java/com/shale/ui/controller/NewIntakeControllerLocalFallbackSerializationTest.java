package com.shale.ui.controller;

import com.google.gson.Gson;
import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NewIntakeControllerLocalFallbackSerializationTest {
	private static final Gson GSON = new Gson();

	@Test
	void localDraftPayloadSerializesDatesAsIsoStringsWithoutLocalDateReflection() {
		NewIntakeController.IntakeFormSnapshot snapshot = snapshotWithDates();

		String json = assertDoesNotThrow(() -> GSON.toJson(NewIntakeController.toLocalDraftPayload(snapshot)));

		assertTrue(json.contains("\"dateOfIntake\":\"2026-07-09\""));
		assertTrue(json.contains("\"clientDateOfBirth\":\"1980-01-02\""));
		assertFalse(json.contains("\"year\""), "LocalDate internals must not be serialized reflectively");
		assertFalse(json.contains("\"month\""), "LocalDate internals must not be serialized reflectively");
	}

	@Test
	void localDraftPayloadRoundTripsDatesAndParties() {
		NewIntakeController.IntakeFormSnapshot snapshot = snapshotWithDates();
		String json = GSON.toJson(NewIntakeController.toLocalDraftPayload(snapshot));
		NewIntakeController.LocalDraftPayload parsed = GSON.fromJson(json, NewIntakeController.LocalDraftPayload.class);

		NewIntakeController.IntakeFormSnapshot restored = NewIntakeController.fromLocalDraftPayload(parsed);

		assertEquals(LocalDate.of(2026, 7, 9), restored.dateOfIntake());
		assertEquals(LocalDate.of(1980, 1, 2), restored.clientDateOfBirth());
		assertEquals(LocalDate.of(2026, 6, 1), restored.medicalNegligenceDate());
		assertEquals("Client", restored.pendingParties().getFirst().entityLabel());
	}

	@Test
	void copyIntakeTextIsReadableAndHandlesDates() {
		String text = assertDoesNotThrow(() -> NewIntakeController.toReadableIntakeText(snapshotWithDates()));

		assertTrue(text.startsWith("New Intake Backup"));
		assertTrue(text.contains("Case name: Smith Intake"));
		assertTrue(text.contains("Date of intake: 2026-07-09"));
		assertTrue(text.contains("Client date of birth: 1980-01-02"));
		assertFalse(text.contains("java.time.LocalDate"));
		assertFalse(text.contains("{\"version\""));
	}

	@Test
	void dialogButtonSizingSupportsFallbackLabels() {
		String source = assertDoesNotThrow(() -> java.nio.file.Files.readString(
				java.nio.file.Path.of("src/main/java/com/shale/ui/component/dialog/AppDialogs.java")));

		assertTrue(source.contains("button.setMinWidth(buttonWidth)"));
		assertTrue(source.contains("button.setPrefWidth(buttonWidth)"));
		assertTrue(source.contains("Math.max(128"));
	}

	private static NewIntakeController.IntakeFormSnapshot snapshotWithDates() {
		return new NewIntakeController.IntakeFormSnapshot(
				"Smith Intake",
				LocalDate.of(2026, 7, 9),
				"09:30",
				false,
				"Jane",
				"Smith",
				"123 Main St",
				"555-0100",
				"jane@example.com",
				LocalDate.of(1980, 1, 2),
				false,
				"Stable",
				true,
				"",
				"",
				"",
				"",
				"",
				10,
				20,
				"Description text",
				"Summary text",
				LocalDate.of(2026, 6, 1),
				LocalDate.of(2026, 6, 2),
				LocalDate.of(2026, 6, 3),
				LocalDate.of(2026, 6, 4),
				LocalDate.of(2026, 6, 5),
				List.of(new PartyAddWorkflowDialog.AddPartyDraft(
						"contact", 123L, "Client", 1L, "plaintiff", true, "party notes", false,
						"Jane", "Smith", "", null)));
	}

}
