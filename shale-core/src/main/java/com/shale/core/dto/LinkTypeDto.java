package com.shale.core.dto;

import java.util.Arrays;

public record LinkTypeDto(
		int id,
		Integer shaleClientId,
		String name,
		String color,
		boolean active,
		boolean deleted,
		String systemKey,
		byte[] rowVer) {
	public LinkTypeDto {
		rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
	}

	@Override
	public byte[] rowVer() {
		return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
	}
}
