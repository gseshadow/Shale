package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;

/**
 * Unified case-party row DTO for contact and organization party links.
 */
public final class CasePartyDto {

	private final long id;
	private final long caseId;
	private final Long contactId;
	private final Long organizationId;
	private final long partyRoleId;
	private final String partyRoleName;
	private final String partyRoleSystemKey;
	private final String side;
	private final boolean primary;
	private final String notes;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;
	private final String entityType;
	private final String displayName;
	private final String email;
	private final String phone;
	private final List<String> credentialAbbreviations;
	private final List<ClassificationPresentation> classifications;

	public CasePartyDto(long id,
			long caseId,
			Long contactId,
			Long organizationId,
			long partyRoleId,
			String partyRoleName,
			String partyRoleSystemKey,
			String side,
			boolean primary,
			String notes,
			LocalDateTime createdAt,
			LocalDateTime updatedAt,
			String entityType,
			String displayName,
			String email,
			String phone,
			List<String> credentialAbbreviations,
			List<ClassificationPresentation> classifications) {
		this.id = id;
		this.caseId = caseId;
		this.contactId = contactId;
		this.organizationId = organizationId;
		this.partyRoleId = partyRoleId;
		this.partyRoleName = partyRoleName == null ? "" : partyRoleName;
		this.partyRoleSystemKey = partyRoleSystemKey == null ? "" : partyRoleSystemKey;
		this.side = side == null ? "" : side;
		this.primary = primary;
		this.notes = notes == null ? "" : notes;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.entityType = entityType == null ? "" : entityType;
		this.displayName = displayName == null ? "" : displayName;
		this.email = email == null ? "" : email;
		this.phone = phone == null ? "" : phone;
		this.credentialAbbreviations = List.copyOf(credentialAbbreviations);
		this.classifications = List.copyOf(classifications);
	}

	public long getId() { return id; }
	public long getCaseId() { return caseId; }
	public Long getContactId() { return contactId; }
	public Long getOrganizationId() { return organizationId; }
	public long getPartyRoleId() { return partyRoleId; }
	public String getPartyRoleName() { return partyRoleName; }
	public String getPartyRoleSystemKey() { return partyRoleSystemKey; }
	public String getSide() { return side; }
	public boolean isPrimary() { return primary; }
	public String getNotes() { return notes; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public String getEntityType() { return entityType; }
	public String getDisplayName() { return displayName; }
	public String getEmail() { return email; }
	public String getPhone() { return phone; }
	public List<String> getCredentialAbbreviations() { return credentialAbbreviations; }
	public List<ClassificationPresentation> getClassifications() { return classifications; }
}
