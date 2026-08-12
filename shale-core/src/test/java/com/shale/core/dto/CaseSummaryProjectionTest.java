package com.shale.core.dto;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class CaseSummaryProjectionTest {
	@Test
	void duplicateLabelsDoNotCollapseAuthoritativeIdentity() {
		CaseSummaryProjection first = projection(41, 101, 201);
		CaseSummaryProjection second = projection(42, 102, 202);
		assertNotEquals(first, second);
		assertNotEquals(first.primaryStatusId(), second.primaryStatusId());
		assertNotEquals(first.responsibleAttorneyId(), second.responsibleAttorneyId());
	}

	private static CaseSummaryProjection projection(long caseId, int statusId, int userId) {
		return new CaseSummaryProjection(caseId, 7, "C-1", "Same", statusId, "same", "same", "Same", "#fff",
				3, "Same", userId, "Same", "#fff", null, null, null, null, null, false);
	}
}
