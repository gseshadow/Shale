package com.shale.core.dto;

import java.util.Arrays;
import java.util.List;

/** Resolved per-case Overview date layout. An empty configured list is meaningful. */
public record CaseOverviewDateConfigurationDto(long caseId, boolean customized,
		List<EffectiveCaseDateTypeDto> visibleDateTypes, byte[] rowVer) {
	public CaseOverviewDateConfigurationDto {
		visibleDateTypes = visibleDateTypes == null ? List.of() : List.copyOf(visibleDateTypes);
		rowVer = copy(rowVer);
	}
	@Override public byte[] rowVer() { return copy(rowVer); }
	private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
