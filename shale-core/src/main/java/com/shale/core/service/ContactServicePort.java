package com.shale.core.service;

import java.util.List;
import java.util.Optional;

/**
 * Shared contact application boundary for future desktop/server adapters.
 *
 * <p>Contact DTOs do not currently live in shale-core, so this port defines
 * minimal API-safe records instead of depending on shale-data DAO row types.</p>
 */
public interface ContactServicePort {

	List<ContactSummary> searchContacts(int shaleClientId, String query, int limit);

	Optional<ContactDetail> getContactDetail(int contactId, int shaleClientId);

	List<Definition> getEffectiveContactTypes(int shaleClientId);

	List<Definition> getEffectiveSpecialties(int shaleClientId);

	List<CredentialDefinition> getEffectiveCredentialDefinitions(int shaleClientId);

	/** Administrator-only lifecycle view of one closed definition category. */
	List<AdministrationDefinition> listDefinitionsForAdministration(
			DefinitionCategory category, int shaleClientId, int actorUserId);

	Optional<ClassificationProfile> getClassificationProfile(int contactId, int shaleClientId);

	int createContact(CreateContactCommand command);

	/**
	 * TODO: align this placeholder command with ContactDao.ContactProfileUpdateRequest.
	 */
	boolean updateContact(UpdateContactCommand command);

	boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId);

	DefinitionMutationResult createDefinition(CreateDefinitionCommand command);

	DefinitionMutationResult updateDefinition(UpdateDefinitionCommand command);

	DefinitionMutationResult setDefinitionActive(DefinitionLifecycleCommand command);

	DefinitionMutationResult removeDefinition(DefinitionLifecycleCommand command);

	DefinitionMutationResult restoreDefinition(DefinitionLifecycleCommand command);

	AssignmentMutationResult assignClassification(AssignClassificationCommand command);

	AssignmentMutationResult removeClassification(AssignmentLifecycleCommand command);

	AssignmentMutationResult restoreClassification(AssignmentLifecycleCommand command);

	List<AssignmentMutationResult> reorderCredentials(ReorderCredentialsCommand command);

	record ContactSummary(int id, String displayName, String email, String phone) {
	}

	record ContactDetail(
			int id,
			int shaleClientId,
			String name,
			String firstName,
			String lastName,
			String displayName,
			String email,
			String phone,
			String addressHome,
			String dateOfBirth,
			String condition,
			boolean deceased,
			boolean client) {
	}

	/** A selectable effective Contact Type or Specialty; the id is always the stored definition id. */
	record Definition(int id, String systemKey, String name, String description, String color, int sortOrder) {
	}

	/** Professional credentials retain both their full name and abbreviation. */
	record CredentialDefinition(int id, String systemKey, String name, String abbreviation,
			String description, String color, int sortOrder) {
	}

	enum DefinitionOrigin { GLOBAL, CUSTOM, OVERRIDE }
	enum DefinitionOverlayState { EFFECTIVE, OVERRIDDEN, MASKED_GLOBAL, GLOBAL_FALLBACK, INEFFECTIVE }

	record AdministrationDefinition(DefinitionCategory category, int id, Integer shaleClientId,
			String systemKey, String name, String abbreviation, String description, String color, int sortOrder,
			boolean active, boolean deleted, DefinitionOrigin origin, Integer relatedGlobalDefinitionId,
			DefinitionOverlayState overlayState, byte[] rowVer) {
		public AdministrationDefinition { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
		public boolean global() { return origin == DefinitionOrigin.GLOBAL; }
		public boolean effective() { return overlayState == DefinitionOverlayState.EFFECTIVE
				|| overlayState == DefinitionOverlayState.GLOBAL_FALLBACK; }
	}

	record StructuredName(String prefix, String firstName, String middleName, String lastName,
			String preferredName, String suffix) {
	}

	record AssignedDefinition(long assignmentId, Definition definition, boolean historical) {
	}

	record AssignedCredential(long assignmentId, CredentialDefinition definition, int displayOrder,
			boolean historical) {
	}

	/** Read-only classification aggregate. It deliberately carries the existing display name unchanged. */
	record ClassificationProfile(int contactId, int shaleClientId, StructuredName structuredName,
			String legacyDisplayName, List<AssignedDefinition> contactTypes,
			List<AssignedDefinition> specialties, List<AssignedCredential> credentials) {
		public ClassificationProfile {
			contactTypes = List.copyOf(contactTypes);
			specialties = List.copyOf(specialties);
			credentials = List.copyOf(credentials);
		}
	}

	record CreateContactCommand(
			int shaleClientId,
			int actorUserId,
			String name,
			String firstName,
			String lastName,
			String email,
			String phone,
			String addressHome,
			String dateOfBirth,
			String condition,
			Boolean deceased) {
	}

	record UpdateContactCommand(
			int contactId,
			int shaleClientId,
			int actorUserId,
			String name,
			String firstName,
			String lastName,
			String email,
			String phone,
			String addressHome,
			String dateOfBirth,
			String condition,
			Boolean deceased) {
	}

	enum DefinitionCategory { CONTACT_TYPE, SPECIALTY, CREDENTIAL }

	record CreateDefinitionCommand(DefinitionCategory category, int shaleClientId, int actorUserId,
			String systemKey, Integer globalDefinitionId, String name, String abbreviation,
			String description, String color, int sortOrder, boolean active) { }

	record UpdateDefinitionCommand(DefinitionCategory category, int definitionId, int shaleClientId,
			int actorUserId, String name, String abbreviation, String description, String color, int sortOrder,
			byte[] expectedRowVer) {
		public UpdateDefinitionCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record DefinitionLifecycleCommand(DefinitionCategory category, int definitionId, int shaleClientId,
			int actorUserId, boolean active, byte[] expectedRowVer) {
		public DefinitionLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record DefinitionMutationResult(DefinitionCategory category, int definitionId, String systemKey, String color,
			boolean active, boolean deleted, byte[] rowVer) {
		public DefinitionMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record AssignClassificationCommand(DefinitionCategory category, int shaleClientId, int actorUserId,
			int contactId, int definitionId, Integer displayOrder) { }

	record AssignmentLifecycleCommand(DefinitionCategory category, int shaleClientId, int actorUserId,
			int contactId, long assignmentId, byte[] expectedRowVer) {
		public AssignmentLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record AssignmentMutationResult(DefinitionCategory category, long assignmentId, int contactId,
			int definitionId, Integer displayOrder, boolean deleted, byte[] rowVer) {
		public AssignmentMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record CredentialOrderItem(long assignmentId, byte[] expectedRowVer) {
		public CredentialOrderItem { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record ReorderCredentialsCommand(int shaleClientId, int actorUserId, int contactId,
			List<CredentialOrderItem> orderedAssignments) {
		public ReorderCredentialsCommand { orderedAssignments = orderedAssignments == null ? List.of() : List.copyOf(orderedAssignments); }
	}

	private static byte[] copyRowVer(byte[] value) { return value == null ? null : value.clone(); }

}
