package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shale.core.service.ContactServicePort.ContactDetail;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactSummary;
import com.shale.core.service.ContactServicePort.ClassificationProfile;
import com.shale.data.dao.ContactDao;

class ContactServiceAdapterTest {

	@Test
	void searchContactsDelegatesAndMapsRows() {
		FakeContactGateway gateway = new FakeContactGateway(List.of(
				new ContactDao.DirectoryContactRow(1, "Ada", "Lovelace", "Ada Lovelace", "ada@example.com", "555")));
		ContactServiceAdapter adapter = new ContactServiceAdapter(gateway);

		List<ContactSummary> summaries = adapter.searchContacts(42, "ada", 10);

		assertEquals(42, gateway.lastSearchShaleClientId);
		assertEquals("ada", gateway.lastSearchQuery);
		assertEquals(List.of(new ContactSummary(1, "Ada Lovelace", "ada@example.com", "555")), summaries);
	}

	@Test
	void directoryCardProjectionCarriesAuthoritativeOrderedCredentialsInOneGatewayCall() {
		FakeContactGateway gateway = new FakeContactGateway(List.of());
		gateway.directoryPage = new ContactDao.PagedResult<>(List.of(
				new ContactDao.ContactCardSummaryRow(1, "Example Doctor", "doctor@example.test", "555", List.of("M.D.", "Ph.D.")),
				new ContactDao.ContactCardSummaryRow(2, "No Credentials", null, null, List.of())), 0, 100, 2);

		var page = new ContactServiceAdapter(gateway).getContactDirectoryPage(42, 9, 0, 100, "doctor", ContactServicePort.DirectoryFilters.EMPTY);

		assertEquals(1, gateway.directoryPageCalls, "the complete page uses one bounded gateway query, not one query per card");
		assertEquals(42, gateway.directoryTenant);
		assertEquals(List.of("M.D.", "Ph.D."), page.items().get(0).credentialAbbreviations());
		assertEquals("Example Doctor M.D., Ph.D.", page.items().get(0).displayName());
		assertEquals("No Credentials", page.items().get(1).displayName());
	}

	@Test
	void getContactDetailReturnsEmptyWhenDelegateCannotFindContact() {
		ContactServiceAdapter adapter = new ContactServiceAdapter(new FakeContactGateway(List.of()));

		Optional<ContactDetail> detail = adapter.getContactDetail(404, 42);

		assertFalse(detail.isPresent());
	}

	@Test
	void getContactDetailUsesFullContactShapeForApiDto() {
		FakeContactGateway gateway = new FakeContactGateway(List.of(
				new ContactDao.DirectoryContactRow(1, "Ada", "Lovelace", "Ada Lovelace", "ada@example.com", "555")));
		ContactServiceAdapter adapter = new ContactServiceAdapter(gateway);

		Optional<ContactDetail> detail = adapter.getContactDetail(1, 42);

		assertTrue(detail.isPresent());
		assertEquals(new ContactDetail(1, 42, "Ada Lovelace", "Ada", "Lovelace", "Ada Lovelace", "ada@example.com", "555", "123 Main", "1815-12-10", "", false, true),
				detail.orElseThrow());
		assertEquals(1, gateway.lastDetailContactId);
		assertEquals(42, gateway.lastDetailShaleClientId);
		assertTrue(gateway.fullDetailLookupCalled);
	}

	@Test
	void classificationReadsCarryStructuredNamesHistoricalStateAndAuthoritativeIds() {
		FakeContactGateway gateway = new FakeContactGateway(List.of());
		ContactDao.DefinitionRow historical = new ContactDao.DefinitionRow(91, "expert", "Expert", null, "#112233", 2, false, true);
		ContactDao.CredentialDefinitionRow credential = new ContactDao.CredentialDefinitionRow(
				73, "doctor_of_medicine", "Doctor of Medicine", "MD", null, "#445566", 1, true, false);
		byte[] typeRowVer = { 11, 12 };
		byte[] credentialRowVer = { 21, 22 };
		Instant updatedAt = Instant.parse("2026-08-26T12:00:00Z");
		gateway.profile = new ContactDao.ClassificationProfileRow(5, 42, "Dr.", "Ada", "Byron", "Lovelace",
				"Ada", "III", "Ada Lovelace", updatedAt,
				List.of(new ContactDao.AssignedDefinitionRow(1001, historical, true, typeRowVer)),
				List.of(), List.of(new ContactDao.AssignedCredentialRow(1002, credential, 4, false, credentialRowVer)));
		typeRowVer[0] = 99;
		credentialRowVer[0] = 99;

		ClassificationProfile profile = new ContactServiceAdapter(gateway).getClassificationProfile(5, 42).orElseThrow();

		assertEquals("Ada Lovelace", profile.legacyDisplayName());
		assertEquals("III", profile.structuredName().suffix());
		assertEquals(91, profile.contactTypes().get(0).definition().id());
		assertEquals(1001, profile.contactTypes().get(0).assignmentId());
		assertArrayEquals(new byte[] { 11, 12 }, profile.contactTypes().get(0).rowVer());
		assertTrue(profile.contactTypes().get(0).historical());
		assertEquals("#112233", profile.contactTypes().get(0).definition().color());
		assertEquals("Doctor of Medicine", profile.credentials().get(0).definition().name());
		assertEquals("MD", profile.credentials().get(0).definition().abbreviation());
		assertEquals(1002, profile.credentials().get(0).assignmentId());
		assertArrayEquals(new byte[] { 21, 22 }, profile.credentials().get(0).rowVer());
		assertEquals("#445566", profile.credentials().get(0).definition().color());
		assertEquals(4, profile.credentials().get(0).displayOrder());
		assertEquals(updatedAt, profile.contactUpdatedAt());
		byte[] exposedTypeRowVer = profile.contactTypes().get(0).rowVer();
		exposedTypeRowVer[0] = 88;
		assertArrayEquals(new byte[] { 11, 12 }, profile.contactTypes().get(0).rowVer());
		byte[] exposedCredentialRowVer = profile.credentials().get(0).rowVer();
		exposedCredentialRowVer[0] = 88;
		assertArrayEquals(new byte[] { 21, 22 }, profile.credentials().get(0).rowVer());
	}

	@Test
	void aggregateProfileUpdateDelegatesExactCommandAndReturnsAuthoritativeReload() {
		FakeContactGateway gateway = new FakeContactGateway(List.of());
		Instant expectedUpdatedAt = Instant.parse("2026-08-26T12:00:00Z");
		Instant authoritativeUpdatedAt = Instant.parse("2026-08-26T12:01:00Z");
		byte[] typeRowVer = { 1, 2 };
		byte[] specialtyRowVer = { 3, 4 };
		byte[] credentialOneRowVer = { 5, 6 };
		byte[] credentialTwoRowVer = { 7, 8 };
		var typeIntent = new com.shale.core.service.ContactServicePort.IntendedAssignment(1001, 91, true, typeRowVer);
		var specialtyIntent = new com.shale.core.service.ContactServicePort.IntendedAssignment(2001, 81, false, specialtyRowVer);
		var credentialOne = new com.shale.core.service.ContactServicePort.IntendedAssignment(3002, 73, true, credentialOneRowVer);
		var credentialTwo = new com.shale.core.service.ContactServicePort.IntendedAssignment(3001, 72, true, credentialTwoRowVer);
		var structuredName = new com.shale.core.service.ContactServicePort.StructuredName(
				"Dr.", "Ada", "Byron", "Lovelace", "Ada", "III");
		var command = new com.shale.core.service.ContactServicePort.UpdateContactProfileCommand(
				5, 42, 7, "Countess Lovelace", structuredName, expectedUpdatedAt,
				List.of(typeIntent), List.of(specialtyIntent), List.of(credentialOne, credentialTwo));

		ContactDao.DefinitionRow typeDefinition = new ContactDao.DefinitionRow(
				91, "expert", "Expert", null, "#112233", 0, true, false);
		ContactDao.CredentialDefinitionRow credentialDefinition = new ContactDao.CredentialDefinitionRow(
				73, "doctor_of_medicine", "Doctor of Medicine", "MD", null, "#445566", 0, true, false);
		gateway.aggregateReloadProfile = new ContactDao.ClassificationProfileRow(
				5, 42, "Dr.", "Ada", "Byron", "Lovelace", "Ada", "III", "Countess Lovelace",
				authoritativeUpdatedAt,
				List.of(new ContactDao.AssignedDefinitionRow(1001, typeDefinition, false, new byte[] { 31, 32 })),
				List.of(), List.of(new ContactDao.AssignedCredentialRow(
						3002, credentialDefinition, 0, false, new byte[] { 41, 42 })));

		var result = new ContactServiceAdapter(gateway).updateContactProfile(command);

		assertEquals(command, gateway.aggregateCommand);
		assertEquals(5, gateway.aggregateCommand.contactId());
		assertEquals(42, gateway.aggregateCommand.shaleClientId());
		assertEquals(7, gateway.aggregateCommand.actorUserId());
		assertEquals("Countess Lovelace", gateway.aggregateCommand.displayName());
		assertEquals(structuredName, gateway.aggregateCommand.structuredName());
		assertEquals(expectedUpdatedAt, gateway.aggregateCommand.expectedContactUpdatedAt());
		assertEquals(List.of(typeIntent), gateway.aggregateCommand.contactTypes());
		assertEquals(List.of(specialtyIntent), gateway.aggregateCommand.specialties());
		assertEquals(List.of(credentialOne, credentialTwo), gateway.aggregateCommand.credentials());
		assertEquals(1001, gateway.aggregateCommand.contactTypes().get(0).assignmentId());
		assertEquals(91, gateway.aggregateCommand.contactTypes().get(0).definitionId());
		assertTrue(gateway.aggregateCommand.contactTypes().get(0).selected());
		assertArrayEquals(new byte[] { 1, 2 }, gateway.aggregateCommand.contactTypes().get(0).expectedRowVer());
		assertEquals(2001, gateway.aggregateCommand.specialties().get(0).assignmentId());
		assertEquals(81, gateway.aggregateCommand.specialties().get(0).definitionId());
		assertFalse(gateway.aggregateCommand.specialties().get(0).selected());
		assertArrayEquals(new byte[] { 3, 4 }, gateway.aggregateCommand.specialties().get(0).expectedRowVer());
		assertEquals(3002, gateway.aggregateCommand.credentials().get(0).assignmentId());
		assertEquals(73, gateway.aggregateCommand.credentials().get(0).definitionId());
		assertTrue(gateway.aggregateCommand.credentials().get(0).selected());
		assertArrayEquals(new byte[] { 5, 6 }, gateway.aggregateCommand.credentials().get(0).expectedRowVer());
		assertEquals(3001, gateway.aggregateCommand.credentials().get(1).assignmentId());
		assertEquals(72, gateway.aggregateCommand.credentials().get(1).definitionId());
		assertTrue(gateway.aggregateCommand.credentials().get(1).selected());
		assertArrayEquals(new byte[] { 7, 8 }, gateway.aggregateCommand.credentials().get(1).expectedRowVer());
		assertEquals(authoritativeUpdatedAt, result.contactUpdatedAt());
		assertEquals("Countess Lovelace", result.profile().legacyDisplayName());
		assertEquals("Countess Lovelace", gateway.aggregateCommand.displayName(),
				"the editable stored DisplayName must remain the uncomposed base value");
		assertEquals(0, result.profile().credentials().get(0).displayOrder());
		assertEquals("#112233", result.profile().contactTypes().get(0).definition().color());
		assertFalse(result.profile().contactTypes().get(0).historical());
		assertArrayEquals(new byte[] { 31, 32 }, result.profile().contactTypes().get(0).rowVer());
		byte[] returned = result.profile().credentials().get(0).rowVer();
		returned[0] = 99;
		assertArrayEquals(new byte[] { 41, 42 }, result.profile().credentials().get(0).rowVer());
		typeRowVer[0] = 99;
		credentialOneRowVer[0] = 99;
		assertArrayEquals(new byte[] { 1, 2 }, gateway.aggregateCommand.contactTypes().get(0).expectedRowVer());
		assertArrayEquals(new byte[] { 5, 6 }, gateway.aggregateCommand.credentials().get(0).expectedRowVer());
	}

	@Test
	void phase1cMutationCommandsRemainOnSharedPortAndDelegateUnchanged() {
		FakeContactGateway gateway = new FakeContactGateway(List.of());
		var command = new com.shale.core.service.ContactServicePort.AssignClassificationCommand(
				com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 42, 7, 5, 91, null);
		var expected = new com.shale.core.service.ContactServicePort.AssignmentMutationResult(
				command.category(), 1001, 5, 91, null, false, new byte[] { 1 });
		gateway.assignmentResult = expected;
		assertEquals(expected, new ContactServiceAdapter(gateway).assignClassification(command));
		assertEquals(command, gateway.assignmentCommand);
	}

	@Test
	void administrationProjectionDistinguishesOverrideMaskFallbackAndCustomRows() {
		FakeContactGateway gateway = new FakeContactGateway(List.of());
		gateway.adminRows = List.of(
				new ContactDao.AdministrationDefinitionRow(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 1, null, "expert", "Expert", null, null, "#111111", 0, true, false, new byte[]{1}),
				new ContactDao.AdministrationDefinitionRow(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 2, 42, "expert", "Local Expert", null, null, "#222222", 0, false, false, new byte[]{2}),
				new ContactDao.AdministrationDefinitionRow(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 3, null, "provider", "Provider", null, null, "#333333", 1, true, false, new byte[]{3}),
				new ContactDao.AdministrationDefinitionRow(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 4, 42, "provider", "Local Provider", null, null, "#444444", 1, false, true, new byte[]{4}),
				new ContactDao.AdministrationDefinitionRow(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 5, 42, "vendor", "Vendor", null, null, "#555555", 2, true, false, new byte[]{5}));
		var result = new ContactServiceAdapter(gateway).listDefinitionsForAdministration(
				com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 42, 7);
		assertEquals(com.shale.core.service.ContactServicePort.DefinitionOverlayState.MASKED_GLOBAL, result.get(0).overlayState());
		assertEquals(com.shale.core.service.ContactServicePort.DefinitionOrigin.OVERRIDE, result.get(1).origin());
		assertEquals(Integer.valueOf(1), result.get(1).relatedGlobalDefinitionId());
		assertEquals("#111111", result.get(0).color());
		assertEquals("#222222", result.get(1).color());
		assertEquals(com.shale.core.service.ContactServicePort.DefinitionOverlayState.GLOBAL_FALLBACK, result.get(2).overlayState());
		assertEquals(com.shale.core.service.ContactServicePort.DefinitionOrigin.CUSTOM, result.get(4).origin());
		byte[] bytes=result.get(4).rowVer(); bytes[0]=9; assertEquals(5,result.get(4).rowVer()[0]);
	}

	private static final class FakeContactGateway implements ContactServiceAdapter.ContactGateway {
		private final List<ContactDao.DirectoryContactRow> rows;
		private int lastSearchShaleClientId;
		private String lastSearchQuery;
		private int lastDetailContactId;
		private int lastDetailShaleClientId;
		private boolean fullDetailLookupCalled;
		private ContactDao.ClassificationProfileRow profile;
		private com.shale.core.service.ContactServicePort.AssignClassificationCommand assignmentCommand;
		private com.shale.core.service.ContactServicePort.AssignmentMutationResult assignmentResult;
		private List<ContactDao.AdministrationDefinitionRow> adminRows = List.of();
		private com.shale.core.service.ContactServicePort.UpdateContactProfileCommand aggregateCommand;
		private ContactDao.ClassificationProfileRow aggregateReloadProfile;
		private ContactDao.PagedResult<ContactDao.ContactCardSummaryRow> directoryPage =
				new ContactDao.PagedResult<>(List.of(), 0, 100, 0);
		private int directoryPageCalls;
		private int directoryTenant;

		private FakeContactGateway(List<ContactDao.DirectoryContactRow> rows) {
			this.rows = rows;
		}

		@Override
		public List<ContactDao.DirectoryContactRow> searchContacts(int shaleClientId, String query) {
			lastSearchShaleClientId = shaleClientId;
			lastSearchQuery = query;
			return rows;
		}

		@Override
		public ContactDao.PagedResult<ContactDao.ContactCardSummaryRow> findDirectoryContactsPage(
				int shaleClientId, int actorUserId, int page, int pageSize, String query, ContactServicePort.DirectoryFilters filters) {
			directoryPageCalls++;
			directoryTenant = shaleClientId;
			return directoryPage;
		}

		@Override
		public ContactDao.DirectoryContactRow findDirectoryContactById(int contactId, int shaleClientId) {
			lastDetailContactId = contactId;
			lastDetailShaleClientId = shaleClientId;
			return rows.stream()
					.filter(row -> row.id() == contactId)
					.findFirst()
					.orElse(null);
		}

		@Override
		public ContactDao.ContactDetailRow findById(int contactId, int shaleClientId) {
			lastDetailContactId = contactId;
			lastDetailShaleClientId = shaleClientId;
			fullDetailLookupCalled = true;
			return rows.stream().anyMatch(row -> row.id() == contactId) ? detailRow() : null;
		}

		@Override
		public int createContact(ContactDao.CreateContactRequest request) {
			return 0;
		}

		@Override
		public boolean updateBasicProfile(ContactDao.ContactProfileUpdateRequest request) {
			return false;
		}

		@Override
		public boolean softDeleteContact(int contactId, int shaleClientId) {
			return false;
		}

		@Override public ContactDao.ClassificationProfileRow findClassificationProfile(int contactId, int shaleClientId) {
			return profile;
		}
		@Override public List<ContactDao.AdministrationDefinitionRow> listDefinitionsForAdministration(
				com.shale.core.service.ContactServicePort.DefinitionCategory category, int tenant, int actor) { return adminRows; }

		@Override public com.shale.core.service.ContactServicePort.AssignmentMutationResult assignClassification(
				com.shale.core.service.ContactServicePort.AssignClassificationCommand command) {
			assignmentCommand = command; return assignmentResult;
		}

		@Override public void updateContactProfile(
				com.shale.core.service.ContactServicePort.UpdateContactProfileCommand command) {
			aggregateCommand = command;
			profile = aggregateReloadProfile;
		}

		@SuppressWarnings("unused")
		private static ContactDao.ContactDetailRow detailRow() {
			return new ContactDao.ContactDetailRow(1, 42, "Ada Lovelace", "Ada", "Lovelace",
					"Ada Lovelace", "ada@example.com", "555", "123 Main", LocalDate.of(1815, 12, 10),
					"", false, true, false, Instant.now());
		}
	}
}
