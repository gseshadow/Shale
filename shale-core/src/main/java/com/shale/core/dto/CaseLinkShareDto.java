package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Arrays;

public record CaseLinkShareDto(
		long caseLinkShareId,
		int shaleClientId,
		long caseLinkId,
		int contactId,
		String contactDisplayName,
		boolean contactUnavailable,
		LocalDateTime sharedAt,
		String notes,
		boolean deleted,
		int createdByUserId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		byte[] rowVer) {
	public CaseLinkShareDto {
		rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
	}

	@Override
	public byte[] rowVer() {
		return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
	}
}
