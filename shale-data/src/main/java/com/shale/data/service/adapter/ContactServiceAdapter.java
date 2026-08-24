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

	private final ContactGateway contactGateway;

	public ContactServiceAdapter(ContactDao contactDao) {
		this(new DaoContactGateway(contactDao));
	}

	ContactServiceAdapter(ContactGateway contactGateway) {
		this.contactGateway = Objects.requireNonNull(contactGateway, "contactGateway");
	}

	@Override
	public List<ContactSummary> searchContacts(int shaleClientId, String query, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return contactGateway.searchContacts(shaleClientId, query).stream()
				.limit(resolvedLimit)
				.map(row -> new ContactSummary(row.id(), row.displayName(), row.email(), row.phone()))
				.toList();
	}

	@Override
	public Optional<ContactDetail> getContactDetail(int contactId, int shaleClientId) {
		return Optional.ofNullable(contactGateway.findById(contactId, shaleClientId))
				.map(row -> new ContactDetail(
						row.id(),
						shaleClientId,
						row.name(),
						row.firstName(),
						row.lastName(),
						row.displayName(),
						row.email(),
						row.phone(),
						row.addressHome(),
						row.dateOfBirth() == null ? null : row.dateOfBirth().toString(),
						row.condition(),
						row.deceased(),
						row.client()));
	}

	@Override
	public List<Definition> getEffectiveContactTypes(int shaleClientId) {
		return contactGateway.listEffectiveDefinitions("ContactTypes", shaleClientId).stream()
				.map(ContactServiceAdapter::definition).toList();
	}

	@Override
	public List<Definition> getEffectiveSpecialties(int shaleClientId) {
		return contactGateway.listEffectiveDefinitions("Specialties", shaleClientId).stream()
				.map(ContactServiceAdapter::definition).toList();
	}

	@Override
	public List<CredentialDefinition> getEffectiveCredentialDefinitions(int shaleClientId) {
		return contactGateway.listEffectiveCredentialDefinitions(shaleClientId).stream()
				.map(ContactServiceAdapter::credentialDefinition).toList();
	}

	@Override
	public Optional<ClassificationProfile> getClassificationProfile(int contactId, int shaleClientId) {
		return Optional.ofNullable(contactGateway.findClassificationProfile(contactId, shaleClientId))
				.map(row -> new ClassificationProfile(row.contactId(), row.shaleClientId(),
						new StructuredName(row.prefix(), row.firstName(), row.middleName(), row.lastName(),
								row.preferredName(), row.suffix()), row.legacyDisplayName(),
						row.contactTypes().stream().map(ContactServiceAdapter::assignedDefinition).toList(),
						row.specialties().stream().map(ContactServiceAdapter::assignedDefinition).toList(),
						row.credentials().stream().map(ContactServiceAdapter::assignedCredential).toList()));
	}

	private static Definition definition(ContactDao.DefinitionRow row) {
		return new Definition(row.id(), row.systemKey(), row.name(), row.description(), row.sortOrder());
	}

	private static CredentialDefinition credentialDefinition(ContactDao.CredentialDefinitionRow row) {
		return new CredentialDefinition(row.id(), row.systemKey(), row.name(), row.abbreviation(),
				row.description(), row.sortOrder());
	}

	private static AssignedDefinition assignedDefinition(ContactDao.AssignedDefinitionRow row) {
		return new AssignedDefinition(row.assignmentId(), definition(row.definition()), row.historical());
	}

	private static AssignedCredential assignedCredential(ContactDao.AssignedCredentialRow row) {
		return new AssignedCredential(row.assignmentId(), credentialDefinition(row.definition()),
				row.displayOrder(), row.historical());
	}

	@Override
	public int createContact(CreateContactCommand command) {
		Objects.requireNonNull(command, "command");
		return contactGateway.createContact(new ContactDao.CreateContactRequest(
				command.shaleClientId(),
				command.actorUserId(),
				command.name(),
				command.firstName(),
				command.lastName(),
				command.email(),
				command.phone(),
				command.addressHome(),
				command.dateOfBirth() == null ? null : java.time.LocalDate.parse(command.dateOfBirth()),
				command.condition(),
				command.deceased() == null ? false : command.deceased(),
				false));
	}

	@Override
	public boolean updateContact(UpdateContactCommand command) {
		Objects.requireNonNull(command, "command");
		ContactDao.ContactDetailRow current = contactGateway.findById(command.contactId(), command.shaleClientId());
		if (current == null) {
			return false;
		}
		return contactGateway.updateBasicProfile(new ContactDao.ContactProfileUpdateRequest(
				command.contactId(),
				command.shaleClientId(),
				command.actorUserId(),
				command.name(),
				command.firstName(),
				command.lastName(),
				command.email(),
				command.phone(),
				command.addressHome(),
				command.dateOfBirth() == null ? null : java.time.LocalDate.parse(command.dateOfBirth()),
				command.condition(),
				command.deceased() == null ? current.deceased() : command.deceased(),
				current.client()));
	}

	@Override
	public boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId) {
		return contactGateway.softDeleteContact(contactId, shaleClientId);
	}

	interface ContactGateway {
		List<ContactDao.DirectoryContactRow> searchContacts(int shaleClientId, String query);

		ContactDao.DirectoryContactRow findDirectoryContactById(int contactId, int shaleClientId);

		ContactDao.ContactDetailRow findById(int contactId, int shaleClientId);

		int createContact(ContactDao.CreateContactRequest request);

		boolean updateBasicProfile(ContactDao.ContactProfileUpdateRequest request);

		boolean softDeleteContact(int contactId, int shaleClientId);

		default List<ContactDao.DefinitionRow> listEffectiveDefinitions(String table, int shaleClientId) { return List.of(); }

		default List<ContactDao.CredentialDefinitionRow> listEffectiveCredentialDefinitions(int shaleClientId) { return List.of(); }

		default ContactDao.ClassificationProfileRow findClassificationProfile(int contactId, int shaleClientId) { return null; }
	}

	private record DaoContactGateway(ContactDao contactDao) implements ContactGateway {
		private DaoContactGateway {
			Objects.requireNonNull(contactDao, "contactDao");
		}

		@Override
		public List<ContactDao.DirectoryContactRow> searchContacts(int shaleClientId, String query) {
			return contactDao.searchContacts(shaleClientId, query);
		}

		@Override
		public ContactDao.ContactDetailRow findById(int contactId, int shaleClientId) {
			return contactDao.findById(contactId, shaleClientId);
		}

		@Override
		public ContactDao.DirectoryContactRow findDirectoryContactById(int contactId, int shaleClientId) {
			return contactDao.findDirectoryContactById(contactId, shaleClientId);
		}

		@Override
		public int createContact(ContactDao.CreateContactRequest request) {
			return contactDao.createContact(request);
		}

		@Override
		public boolean updateBasicProfile(ContactDao.ContactProfileUpdateRequest request) {
			return contactDao.updateBasicProfile(request);
		}

		@Override
		public boolean softDeleteContact(int contactId, int shaleClientId) {
			return contactDao.softDeleteContact(contactId, shaleClientId);
		}

		@Override public List<ContactDao.DefinitionRow> listEffectiveDefinitions(String table, int tenant) {
			return contactDao.listEffectiveDefinitions(table, tenant);
		}
		@Override public List<ContactDao.CredentialDefinitionRow> listEffectiveCredentialDefinitions(int tenant) {
			return contactDao.listEffectiveCredentialDefinitions(tenant);
		}
		@Override public ContactDao.ClassificationProfileRow findClassificationProfile(int contactId, int tenant) {
			return contactDao.findClassificationProfile(contactId, tenant);
		}
	}
}
