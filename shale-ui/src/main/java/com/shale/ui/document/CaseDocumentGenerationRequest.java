package com.shale.ui.document;

import java.util.Objects;

/** Immutable identity/options captured before desktop document generation leaves the FX thread. */
public record CaseDocumentGenerationRequest(int tenantId, int authenticatedUserId, int caseId,
		CaseDocumentType type, CaseDocumentFormat format) {
	public CaseDocumentGenerationRequest {
		if (tenantId <= 0 || authenticatedUserId <= 0 || caseId <= 0)
			throw new IllegalArgumentException("tenantId, authenticatedUserId, and caseId must be > 0");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(format, "format");
	}
}
