package com.shale.ui.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CaseDocumentGenerationRequestTest {
	@Test void capturesAuthoritativeIdentityAndOptions() {
		var request = new CaseDocumentGenerationRequest(7, 11, 42, CaseDocumentType.CASE_SUMMARY,
				CaseDocumentFormat.PDF);
		assertEquals(7, request.tenantId());
		assertEquals(11, request.authenticatedUserId());
		assertEquals(42, request.caseId());
		assertEquals(CaseDocumentType.CASE_SUMMARY, request.type());
		assertEquals(CaseDocumentFormat.PDF, request.format());
	}

	@Test void rejectsIncompleteContext() {
		assertThrows(IllegalArgumentException.class, () -> new CaseDocumentGenerationRequest(
				7, 0, 42, CaseDocumentType.CASE_SUMMARY, CaseDocumentFormat.HTML));
		assertThrows(NullPointerException.class, () -> new CaseDocumentGenerationRequest(
				7, 11, 42, null, CaseDocumentFormat.HTML));
	}
}
