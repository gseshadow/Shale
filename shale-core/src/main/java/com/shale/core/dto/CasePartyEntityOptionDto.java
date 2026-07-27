package com.shale.core.dto;

/** An eligible, tenant-validated Contact or Organization associated to a CaseParty. */
public record CasePartyEntityOptionDto(
		String entityType,
		int entityId,
		String displayName,
		String email,
		String phone,
		String organizationTypeName) {
}
