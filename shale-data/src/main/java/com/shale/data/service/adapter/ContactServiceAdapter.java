package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactNamePresentation;
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
				.map(row -> new ContactSummary(row.id(), presentationName(row, shaleClientId), row.email(), row.phone()))
				.toList();
	}

	@Override
	public DirectoryPage getContactDirectoryPage(int shaleClientId, int actorUserId, int page, int pageSize,
			String query, DirectoryFilters filters) {
		Objects.requireNonNull(filters, "filters");
		ContactDao.PagedResult<ContactDao.ContactCardSummaryRow> result =
				contactGateway.findDirectoryContactsPage(shaleClientId, actorUserId, page, pageSize, query, filters);
		return new DirectoryPage(result.items().stream().map(row -> new ContactCardSummary(
				row.id(), ContactNamePresentation.effectiveDisplayNameFromAbbreviations(row.displayName(), row.credentialAbbreviations()),
				row.email(), row.phone(), row.credentialAbbreviations())).toList(),
				result.page(), result.pageSize(), result.total());
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
						presentationName(row, shaleClientId),
						row.email(),
						row.phone(),
						row.address(),
						row.dateOfBirth() == null ? null : row.dateOfBirth().toString(),
						row.condition(),
						row.notes(),
						row.deceased(),
						row.client()));
	}

	private String presentationName(ContactDao.DirectoryContactRow row, int shaleClientId) {
		return ContactNamePresentation.effectiveDisplayNameFromAbbreviations(
				row.displayName(), row.credentialAbbreviations());
	}

	private String presentationName(ContactDao.ContactDetailRow row, int shaleClientId) {
		ContactDao.ClassificationProfileRow profile = contactGateway.findClassificationProfile(row.id(), shaleClientId);
		if (profile == null) return row.displayName();
		return presentationName(profile);
	}

	private static String presentationName(ContactDao.ClassificationProfileRow row) {
		return ContactNamePresentation.effectiveDisplayName(row.legacyDisplayName(),
				row.credentials().stream().map(ContactServiceAdapter::assignedCredential).toList());
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
	public List<AdministrationDefinition> listDefinitionsForAdministration(DefinitionCategory category,
			int shaleClientId, int actorUserId) {
		List<ContactDao.AdministrationDefinitionRow> rows = contactGateway
				.listDefinitionsForAdministration(category, shaleClientId, actorUserId);
		var globals = rows.stream().filter(r -> r.shaleClientId() == null)
				.collect(java.util.stream.Collectors.toMap(ContactDao.AdministrationDefinitionRow::systemKey,
						java.util.function.Function.identity(), (a, b) -> a));
		var tenants = rows.stream().filter(r -> r.shaleClientId() != null)
				.collect(java.util.stream.Collectors.toMap(ContactDao.AdministrationDefinitionRow::systemKey,
						java.util.function.Function.identity(), (a, b) -> a));
		return rows.stream().map(r -> {
			var global = globals.get(r.systemKey()); var tenant = tenants.get(r.systemKey());
			DefinitionOrigin origin = r.shaleClientId() == null ? DefinitionOrigin.GLOBAL
					: global == null ? DefinitionOrigin.CUSTOM : DefinitionOrigin.OVERRIDE;
			DefinitionOverlayState state;
			if (origin == DefinitionOrigin.GLOBAL) {
				state = tenant == null ? (r.active() && !r.deleted() ? DefinitionOverlayState.EFFECTIVE : DefinitionOverlayState.INEFFECTIVE)
						: tenant.deleted() ? DefinitionOverlayState.GLOBAL_FALLBACK
						: tenant.active() ? DefinitionOverlayState.OVERRIDDEN : DefinitionOverlayState.MASKED_GLOBAL;
			} else state = !r.deleted() && r.active() ? DefinitionOverlayState.EFFECTIVE : DefinitionOverlayState.INEFFECTIVE;
			return new AdministrationDefinition(r.category(), r.id(), r.shaleClientId(), r.systemKey(),
					r.name(), r.abbreviation(), r.description(), r.color(), r.sortOrder(), r.active(), r.deleted(),
					origin, global == null ? null : global.id(), state, r.rowVer());
		}).toList();
	}

	@Override
	public Optional<ClassificationProfile> getClassificationProfile(int contactId, int shaleClientId) {
		return Optional.ofNullable(contactGateway.findClassificationProfile(contactId, shaleClientId))
				.map(row -> new ClassificationProfile(row.contactId(), row.shaleClientId(),
						new StructuredName(row.prefix(), row.firstName(), row.middleName(), row.lastName(),
						row.preferredName(), row.suffix()), row.legacyDisplayName(), row.dateOfBirth(), row.condition(),
						row.notes(), row.deceased(), row.contactUpdatedAt(),
						row.contactTypes().stream().map(ContactServiceAdapter::assignedDefinition).toList(),
						row.specialties().stream().map(ContactServiceAdapter::assignedDefinition).toList(),
						row.credentials().stream().map(ContactServiceAdapter::assignedCredential).toList(),
						row.phoneNumbers().stream().map(x->new ContactPhoneNumber(x.id(),x.kind(),x.displayNumber(),x.normalizedNumber(),x.extension(),x.primary(),x.sortOrder(),x.deleted(),x.createdAt(),x.updatedAt(),x.rowVer())).toList(),
						row.emailAddresses().stream().map(x->new ContactEmailAddress(x.id(),x.kind(),x.emailAddress(),x.normalizedEmail(),x.primary(),x.sortOrder(),x.deleted(),x.createdAt(),x.updatedAt(),x.rowVer())).toList(),
						row.addresses().stream().map(x->new ContactAddress(x.id(),x.kind(),x.addressLine1(),x.addressLine2(),x.city(),x.stateOrProvince(),x.postalCode(),x.countryCode(),x.legacyAddressText(),x.primary(),x.sortOrder(),x.deleted(),x.createdAt(),x.updatedAt(),x.rowVer())).toList()));
	}

	private static Definition definition(ContactDao.DefinitionRow row) {
		return new Definition(row.id(), row.systemKey(), row.name(), row.description(), row.color(), row.sortOrder());
	}

	private static CredentialDefinition credentialDefinition(ContactDao.CredentialDefinitionRow row) {
		return new CredentialDefinition(row.id(), row.systemKey(), row.name(), row.abbreviation(),
				row.description(), row.color(), row.sortOrder());
	}

	private static AssignedDefinition assignedDefinition(ContactDao.AssignedDefinitionRow row) {
		return new AssignedDefinition(row.assignmentId(), definition(row.definition()), row.historical(),row.rowVer());
	}

	private static AssignedCredential assignedCredential(ContactDao.AssignedCredentialRow row) {
		return new AssignedCredential(row.assignmentId(), credentialDefinition(row.definition()),
				row.displayOrder(), row.historical(),row.rowVer());
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
				command.address(),
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
				command.address(),
				command.dateOfBirth() == null ? null : java.time.LocalDate.parse(command.dateOfBirth()),
				command.condition(),
				command.deceased() == null ? current.deceased() : command.deceased(),
				current.client()));
	}

	@Override
	public boolean softDeleteContact(int contactId, int shaleClientId, int actorUserId) {
		return contactGateway.softDeleteContact(contactId, shaleClientId);
	}

	@Override public DefinitionMutationResult createDefinition(CreateDefinitionCommand c) { return contactGateway.createDefinition(c); }
	@Override public DefinitionMutationResult updateDefinition(UpdateDefinitionCommand c) { return contactGateway.updateDefinition(c); }
	@Override public DefinitionMutationResult setDefinitionActive(DefinitionLifecycleCommand c) { return contactGateway.setDefinitionActive(c); }
	@Override public DefinitionMutationResult removeDefinition(DefinitionLifecycleCommand c) { return contactGateway.removeDefinition(c); }
	@Override public DefinitionMutationResult restoreDefinition(DefinitionLifecycleCommand c) { return contactGateway.restoreDefinition(c); }
	@Override public AssignmentMutationResult assignClassification(AssignClassificationCommand c) { return contactGateway.assignClassification(c); }
	@Override public AssignmentMutationResult removeClassification(AssignmentLifecycleCommand c) { return contactGateway.removeClassification(c); }
	@Override public AssignmentMutationResult restoreClassification(AssignmentLifecycleCommand c) { return contactGateway.restoreClassification(c); }
	@Override public List<AssignmentMutationResult> reorderCredentials(ReorderCredentialsCommand c) { return contactGateway.reorderCredentials(c); }
	@Override public ContactProfileMutationResult updateContactProfile(UpdateContactProfileCommand c) {
		contactGateway.updateContactProfile(c);
		ClassificationProfile profile=getClassificationProfile(c.contactId(),c.shaleClientId())
				.orElseThrow(()->new IllegalStateException("Updated Contact profile could not be reloaded."));
		return new ContactProfileMutationResult(profile,profile.contactUpdatedAt());
	}

	interface ContactGateway {
		List<ContactDao.DirectoryContactRow> searchContacts(int shaleClientId, String query);

		ContactDao.DirectoryContactRow findDirectoryContactById(int contactId, int shaleClientId);

		ContactDao.PagedResult<ContactDao.ContactCardSummaryRow> findDirectoryContactsPage(
				int shaleClientId, int actorUserId, int page, int pageSize, String query, DirectoryFilters filters);

		ContactDao.ContactDetailRow findById(int contactId, int shaleClientId);

		int createContact(ContactDao.CreateContactRequest request);

		boolean updateBasicProfile(ContactDao.ContactProfileUpdateRequest request);

		boolean softDeleteContact(int contactId, int shaleClientId);

		default List<ContactDao.DefinitionRow> listEffectiveDefinitions(String table, int shaleClientId) { return List.of(); }

		default List<ContactDao.CredentialDefinitionRow> listEffectiveCredentialDefinitions(int shaleClientId) { return List.of(); }
		default List<ContactDao.AdministrationDefinitionRow> listDefinitionsForAdministration(DefinitionCategory category, int tenant, int actor) { return List.of(); }

		default ContactDao.ClassificationProfileRow findClassificationProfile(int contactId, int shaleClientId) { return null; }
		default DefinitionMutationResult createDefinition(CreateDefinitionCommand c){ throw new UnsupportedOperationException(); }
		default DefinitionMutationResult updateDefinition(UpdateDefinitionCommand c){ throw new UnsupportedOperationException(); }
		default DefinitionMutationResult setDefinitionActive(DefinitionLifecycleCommand c){ throw new UnsupportedOperationException(); }
		default DefinitionMutationResult removeDefinition(DefinitionLifecycleCommand c){ throw new UnsupportedOperationException(); }
		default DefinitionMutationResult restoreDefinition(DefinitionLifecycleCommand c){ throw new UnsupportedOperationException(); }
		default AssignmentMutationResult assignClassification(AssignClassificationCommand c){ throw new UnsupportedOperationException(); }
		default AssignmentMutationResult removeClassification(AssignmentLifecycleCommand c){ throw new UnsupportedOperationException(); }
		default AssignmentMutationResult restoreClassification(AssignmentLifecycleCommand c){ throw new UnsupportedOperationException(); }
		default List<AssignmentMutationResult> reorderCredentials(ReorderCredentialsCommand c){ throw new UnsupportedOperationException(); }
		void updateContactProfile(UpdateContactProfileCommand c);
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
		public ContactDao.PagedResult<ContactDao.ContactCardSummaryRow> findDirectoryContactsPage(
				int shaleClientId, int actorUserId, int page, int pageSize, String query, DirectoryFilters filters) {
			return contactDao.findDirectoryContactsPage(shaleClientId, actorUserId, page, pageSize, query, filters);
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
		@Override public List<ContactDao.AdministrationDefinitionRow> listDefinitionsForAdministration(DefinitionCategory category, int tenant, int actor) {
			return contactDao.listDefinitionsForAdministration(category, tenant, actor);
		}
		@Override public ContactDao.ClassificationProfileRow findClassificationProfile(int contactId, int tenant) {
			return contactDao.findClassificationProfile(contactId, tenant);
		}
		@Override public DefinitionMutationResult createDefinition(CreateDefinitionCommand c){return contactDao.createDefinition(c);}
		@Override public DefinitionMutationResult updateDefinition(UpdateDefinitionCommand c){return contactDao.updateDefinition(c);}
		@Override public DefinitionMutationResult setDefinitionActive(DefinitionLifecycleCommand c){return contactDao.setDefinitionActive(c);}
		@Override public DefinitionMutationResult removeDefinition(DefinitionLifecycleCommand c){return contactDao.removeDefinition(c);}
		@Override public DefinitionMutationResult restoreDefinition(DefinitionLifecycleCommand c){return contactDao.restoreDefinition(c);}
		@Override public AssignmentMutationResult assignClassification(AssignClassificationCommand c){return contactDao.assignClassification(c);}
		@Override public AssignmentMutationResult removeClassification(AssignmentLifecycleCommand c){return contactDao.removeClassification(c);}
		@Override public AssignmentMutationResult restoreClassification(AssignmentLifecycleCommand c){return contactDao.restoreClassification(c);}
		@Override public List<AssignmentMutationResult> reorderCredentials(ReorderCredentialsCommand c){return contactDao.reorderCredentials(c);}
		@Override public void updateContactProfile(UpdateContactProfileCommand c){contactDao.updateContactProfile(c);}
	}
}
