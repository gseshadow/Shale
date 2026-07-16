package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Arrays;

public record CaseLinkDto(
		long caseLinkId,
		long externalLinkId,
		long caseId,
		int shaleClientId,
		int linkTypeId,
		String linkTypeName,
		String linkTypeColor,
		String linkTypeSystemKey,
		String displayName,
		String url,
		String description,
		boolean primary,
		String notes,
		int sortOrder,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		byte[] caseLinkRowVer,
		byte[] externalLinkRowVer) {
	public CaseLinkDto {
		caseLinkRowVer = caseLinkRowVer == null ? null : Arrays.copyOf(caseLinkRowVer, caseLinkRowVer.length);
		externalLinkRowVer = externalLinkRowVer == null ? null : Arrays.copyOf(externalLinkRowVer, externalLinkRowVer.length);
	}

	@Override
	public byte[] caseLinkRowVer() { return caseLinkRowVer == null ? null : Arrays.copyOf(caseLinkRowVer, caseLinkRowVer.length); }
	@Override
	public byte[] externalLinkRowVer() { return externalLinkRowVer == null ? null : Arrays.copyOf(externalLinkRowVer, externalLinkRowVer.length); }
}
