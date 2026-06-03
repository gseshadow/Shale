package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.service.ContactServicePort;
import com.shale.data.dao.ContactDao;

/**
 * Thin ContactServicePort adapter over existing ContactDao operations.
 */
public final class ContactServiceAdapter implements ContactServicePort {

	private final ContactDao contactDao;

	public ContactServiceAdapter(ContactDao contactDao) {
		this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
	}

	@Override
	public List<ContactSummary> searchContacts(int shaleClientId, String query, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return contactDao.searchContacts(shaleClientId, query).stream()
				.limit(resolvedLimit)
				.map(row -> new ContactSummary(row.id(), row.displayName(), row.email(), row.phone()))
				.toList();
	}

	@Override
	public Optional<ContactDetail> getContactDetail(int contactId, int shaleClientId) {
		return Optional.ofNullable(contactDao.findById(contactId, shaleClientId))
				.map(row -> new ContactDetail(
						row.id(),
						row.shaleClientId(),
						row.firstName(),
						row.lastName(),
						row.displayName(),
						row.email(),
						row.phone()));
	}

	@Override
	public int createContact(CreateContactCommand command) {
		Objects.requireNonNull(command, "command");
		return contactDao.createContact(new ContactDao.CreateContactRequest(
				command.shaleClientId(),
				command.firstName(),
				command.lastName(),
				command.email(),
				command.phone(),
				false));
	}

	@Override
	public boolean updateContact(UpdateContactCommand command) {
		Objects.requireNonNull(command, "command");
		ContactDao.ContactDetailRow current = contactDao.findById(command.contactId(), command.shaleClientId());
		if (current == null) {
			return false;
		}
		return contactDao.updateBasicProfile(new ContactDao.ContactProfileUpdateRequest(
				command.contactId(),
				command.shaleClientId(),
				command.actorUserId(),
				current.name(),
				command.firstName(),
				command.lastName(),
				command.email(),
				command.phone(),
				current.addressHome(),
				current.dateOfBirth(),
				current.condition(),
				current.deceased(),
				current.client()));
	}

	@Override
	public boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId) {
		return contactDao.softDeleteContact(contactId, shaleClientId);
	}
}
