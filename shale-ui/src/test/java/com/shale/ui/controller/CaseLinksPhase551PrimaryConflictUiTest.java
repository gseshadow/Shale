package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase551PrimaryConflictUiTest {
	private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

	@Test
	void primaryCreateConflictReloadsAuthoritativeLinksAndShowsSafeMessage() throws Exception {
		String source = Files.readString(CONTROLLER).replace("\r\n", "\n").replace('\r', '\n');
		String mutation = methodSource(source, "private void runCaseLinkMutation(");
		assertTrue(mutation.contains("boolean primaryConflict = isPrimaryCaseLinkConflict(ex)"));
		assertTrue(mutation.contains("caseService.listCaseLinks(activeCaseId, tenantId)"));
		assertTrue(mutation.contains("caseLinks = safeConflictReload"));
		assertTrue(mutation.contains("invalidateOverviewPrimaryLinkAfterCaseLinkMutation()"));
		assertTrue(mutation.contains("AppDialogs.showError(caseLinksOwner(), \"Case Links\", caseLinkUserMessage(ex))"));
	}

	@Test
	void caseLinkUserMessageDoesNotDrillIntoSqlCause() throws Exception {
		String source = Files.readString(CONTROLLER).replace("\r\n", "\n").replace('\r', '\n');
		String userMessage = methodSource(source, "private static String caseLinkUserMessage(");
		assertTrue(userMessage.contains("ex.getMessage()"));
		assertTrue(userMessage.contains("rootMessage(ex)"));
		assertFalse(userMessage.contains("getCause()"));
	}

	private static String methodSource(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, () -> "Missing method " + signature);
		int open = source.indexOf('{', start);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (ch == '{') depth++;
			else if (ch == '}' && --depth == 0) return source.substring(start, i + 1);
		}
		throw new AssertionError("Unbalanced method " + signature);
	}
}
