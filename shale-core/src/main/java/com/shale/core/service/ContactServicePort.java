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

	/**
	 * TODO: align this placeholder command with the existing ContactDao
	 * CreateContactRequest before adding server write endpoints.
	 */
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

	record CreateContactCommand(
			int shaleClientId,
			int actorUserId,
			String firstName,
			String lastName,
			String email,
			String phone) {
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
