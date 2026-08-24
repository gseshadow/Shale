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

	Optional<ClassificationProfile> getClassificationProfile(int contactId, int shaleClientId);

	int createContact(CreateContactCommand command);

	/**
	 * TODO: align this placeholder command with ContactDao.ContactProfileUpdateRequest.
	 */
	boolean updateContact(UpdateContactCommand command);

	boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId);

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
	record Definition(int id, String systemKey, String name, String description, int sortOrder) {
	}

	/** Professional credentials retain both their full name and abbreviation. */
	record CredentialDefinition(int id, String systemKey, String name, String abbreviation,
			String description, int sortOrder) {
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
}
