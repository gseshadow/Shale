package com.shale.core.dto;

import java.time.Instant;

/** A historical-capable role assignment attached to one CaseUsers membership. */
public record CaseTeamMemberRoleDto(long id, int roleDefinitionId, String systemKey, String name,
		boolean definitionActive, boolean definitionDeleted, boolean assignmentDeleted,
		Instant createdAt, Instant updatedAt, byte[] rowVer) {
	public CaseTeamMemberRoleDto { rowVer = rowVer == null ? null : rowVer.clone(); }
	@Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
}
