package com.shale.core.dto;

import java.util.List;

/** Case-team membership is independent from its zero-to-many role assignments. */
public record CaseTeamMembershipDto(long membershipId, long caseId, int userId, String displayName,
		Integer legacyRoleId, boolean primary, byte[] rowVer, List<CaseTeamMemberRoleDto> roles) {
	public CaseTeamMembershipDto {
		rowVer = rowVer == null ? null : rowVer.clone();
		roles = roles == null ? List.of() : List.copyOf(roles);
	}
	@Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
}
