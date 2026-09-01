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

	/** SQL Server nvarchar(max) character capacity documented for dbo.Contacts.Notes. */
	int CONTACT_NOTES_MAX_CHARS = 1_073_741_823;

	List<ContactSummary> searchContacts(int shaleClientId, String query, int limit);

	DirectoryPage getContactDirectoryPage(int shaleClientId, int actorUserId, int page, int pageSize,
			String query, DirectoryFilters filters);

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

	ContactProfileMutationResult updateContactProfile(UpdateContactProfileCommand command);

	record ContactSummary(int id, String displayName, String email, String phone) {
	}

	record ContactCardSummary(int id, String displayName, String email, String phone,
			List<String> credentialAbbreviations) {
		public ContactCardSummary {
			credentialAbbreviations = List.copyOf(credentialAbbreviations);
		}
	}

	record DirectoryPage(List<ContactCardSummary> items, int page, int pageSize, long total) {
		public DirectoryPage { items = List.copyOf(items); }
	}

	/** Authoritative definition identities selected in the directory. */
	record DirectoryFilters(List<Integer> contactTypeIds, List<Integer> specialtyIds,
			List<Integer> credentialIds) {
		public static final DirectoryFilters EMPTY = new DirectoryFilters(List.of(), List.of(), List.of());
		public DirectoryFilters {
			contactTypeIds = List.copyOf(contactTypeIds);
			specialtyIds = List.copyOf(specialtyIds);
			credentialIds = List.copyOf(credentialIds);
		}
		public int activeCount() { return contactTypeIds.size() + specialtyIds.size() + credentialIds.size(); }
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
			String address,
			String dateOfBirth,
			String condition,
			String notes,
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

	record AssignedDefinition(long assignmentId, Definition definition, boolean historical, byte[] rowVer) {
		public AssignedDefinition { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record AssignedCredential(long assignmentId, CredentialDefinition definition, int displayOrder,
			boolean historical, byte[] rowVer) {
		public AssignedCredential { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	/** Read-only classification aggregate. It deliberately carries the existing display name unchanged. */
	record ClassificationProfile(int contactId, int shaleClientId, StructuredName structuredName,
			String legacyDisplayName, java.time.LocalDate dateOfBirth, String condition, String notes, boolean deceased,
			java.time.Instant contactUpdatedAt, List<AssignedDefinition> contactTypes,
			List<AssignedDefinition> specialties, List<AssignedCredential> credentials,
			List<ContactPhoneNumber> phoneNumbers, List<ContactEmailAddress> emailAddresses,
			List<ContactAddress> addresses) {
		public ClassificationProfile {
			contactTypes = List.copyOf(contactTypes);
			specialties = List.copyOf(specialties);
			credentials = List.copyOf(credentials);
			phoneNumbers = List.copyOf(phoneNumbers);
			emailAddresses = List.copyOf(emailAddresses);
			addresses = List.copyOf(addresses);
		}

	}

	record ContactPhoneNumber(long id, String kind, String displayNumber, String normalizedNumber,
			String extension, boolean primary, int sortOrder, boolean deleted,
			java.time.Instant createdAt, java.time.Instant updatedAt, byte[] rowVer) {
		public ContactPhoneNumber { rowVer=copyRowVer(rowVer); }
		@Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}
	record ContactEmailAddress(long id, String kind, String emailAddress, String normalizedEmail,
			boolean primary, int sortOrder, boolean deleted, java.time.Instant createdAt,
			java.time.Instant updatedAt, byte[] rowVer) {
		public ContactEmailAddress { rowVer=copyRowVer(rowVer); }
		@Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}
	record ContactAddress(long id, String kind, String addressLine1, String addressLine2, String city,
			String stateOrProvince, String postalCode, String countryCode, String legacyAddressText,
			boolean primary, int sortOrder, boolean deleted, java.time.Instant createdAt,
			java.time.Instant updatedAt, byte[] rowVer) {
		public ContactAddress { rowVer=copyRowVer(rowVer); }
		@Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}

	record CreateContactCommand(
			int shaleClientId,
			int actorUserId,
			String name,
			String firstName,
			String lastName,
			String email,
			String phone,
			String address,
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
			String address,
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

	record IntendedAssignment(long assignmentId, int definitionId, boolean selected, byte[] expectedRowVer) {
		public IntendedAssignment { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
		public boolean existing() { return assignmentId > 0; }
	}

	record UpdateContactProfileCommand(int contactId, int shaleClientId, int actorUserId,
			String displayName, StructuredName structuredName, java.time.LocalDate dateOfBirth,
			String condition, String notes, boolean deceased, java.time.Instant expectedContactUpdatedAt,
			List<IntendedAssignment> contactTypes, List<IntendedAssignment> specialties,
			List<IntendedAssignment> credentials, List<IntendedPhoneNumber> phoneNumbers,
			List<IntendedEmailAddress> emailAddresses, List<IntendedAddress> addresses) {
		public UpdateContactProfileCommand {
			structuredName = java.util.Objects.requireNonNull(structuredName, "structuredName");
			contactTypes = contactTypes == null ? List.of() : List.copyOf(contactTypes);
			specialties = specialties == null ? List.of() : List.copyOf(specialties);
			credentials = credentials == null ? List.of() : List.copyOf(credentials);
			phoneNumbers = phoneNumbers == null ? List.of() : List.copyOf(phoneNumbers);
			emailAddresses = emailAddresses == null ? List.of() : List.copyOf(emailAddresses);
			addresses = addresses == null ? List.of() : List.copyOf(addresses);
		}

	}

	record IntendedPhoneNumber(Long id, byte[] expectedRowVer, String kind, String displayNumber,
			String extension, boolean primary, boolean deleted, int sortOrder) {
		public IntendedPhoneNumber { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}
	record IntendedEmailAddress(Long id, byte[] expectedRowVer, String kind, String emailAddress,
			boolean primary, boolean deleted, int sortOrder) {
		public IntendedEmailAddress { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}
	record IntendedAddress(Long id, byte[] expectedRowVer, String kind, String addressLine1,
			String addressLine2, String city, String stateOrProvince, String postalCode, String countryCode,
			String legacyAddressText, boolean valuesEdited, boolean primary, boolean deleted, int sortOrder) {
		public IntendedAddress { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}

	record ContactProfileMutationResult(ClassificationProfile profile, java.time.Instant contactUpdatedAt) { }

	private static byte[] copyRowVer(byte[] value) { return value == null ? null : value.clone(); }

}
