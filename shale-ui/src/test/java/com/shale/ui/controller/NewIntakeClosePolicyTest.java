package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class NewIntakeClosePolicyTest {
	private static final Path REPOSITORY_ROOT = repositoryRoot();

	@Test
	void dirtyCloseIsVetoedWhenDiscardIsDeclined() {
		AtomicInteger confirmations = new AtomicInteger();

		boolean allowed = NewIntakeController.evaluateClosePolicy(false, false, true, () -> {
			confirmations.incrementAndGet();
			return false;
		}, () -> { });

		assertFalse(allowed);
		assertEquals(1, confirmations.get());
	}

	@Test
	void dirtyCloseIsAllowedWhenDiscardIsConfirmed() {
		AtomicInteger confirmations = new AtomicInteger();

		boolean allowed = NewIntakeController.evaluateClosePolicy(false, false, true, () -> {
			confirmations.incrementAndGet();
			return true;
		}, () -> { });

		assertTrue(allowed);
		assertEquals(1, confirmations.get());
	}

	@Test
	void untouchedAndSuccessfullyCompletedIntakesCloseWithoutDiscardPrompt() {
		AtomicInteger confirmations = new AtomicInteger();

		assertTrue(NewIntakeController.evaluateClosePolicy(false, false, false,
				() -> { confirmations.incrementAndGet(); return false; }, () -> { }));
		assertTrue(NewIntakeController.evaluateClosePolicy(true, false, true,
				() -> { confirmations.incrementAndGet(); return false; }, () -> { }));
		assertEquals(0, confirmations.get());
	}

	@Test
	void closeIsVetoedWhileSaveIsInProgressWithoutAskingToDiscard() {
		AtomicInteger confirmations = new AtomicInteger();
		AtomicInteger warnings = new AtomicInteger();

		assertFalse(NewIntakeController.evaluateClosePolicy(false, true, true,
				() -> { confirmations.incrementAndGet(); return true; }, warnings::incrementAndGet));
		assertEquals(0, confirmations.get());
		assertEquals(1, warnings.get());
	}

	@Test
	void windowHeaderCancelAndWindowManagerCloseShareControllerPolicy() throws Exception {
		String controller = Files.readString(REPOSITORY_ROOT.resolve(
				"shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));
		String sceneManager = Files.readString(REPOSITORY_ROOT.resolve(
				"shale-ui/src/main/java/com/shale/ui/navigation/SceneManager.java"));
		String fxml = Files.readString(REPOSITORY_ROOT.resolve(
				"shale-ui/src/main/resources/fxml/new-intake.fxml"));
		String compactController = controller.replaceAll("\\s+", "");
		String compactSceneManager = sceneManager.replaceAll("\\s+", "");
		String compactFxml = fxml.replaceAll("\\s+", "");

		assertTrue(compactController.contains("privatevoidonCancel(){requestClose();}"));
		assertTrue(compactController.contains("this.stage.setOnCloseRequest(event->{if(!mayCloseIntake())"));
		assertTrue(compactSceneManager.contains(
				"AppDialogs.createSecondaryWindowHeader(dialog,\"NewIntake\",controller::requestClose)"));
		assertTrue(compactFxml.contains(
				"fx:id=\"cancelButton\"text=\"Cancel\"cancelButton=\"true\"onAction=\"#onCancel\""));
	}

	private static Path repositoryRoot() {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		return Files.isDirectory(workingDirectory.resolve("shale-ui"))
				? workingDirectory
				: workingDirectory.getParent();
	}
}
