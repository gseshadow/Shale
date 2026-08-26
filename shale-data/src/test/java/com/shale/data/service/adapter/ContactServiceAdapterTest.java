package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shale.core.service.ContactServicePort.ContactDetail;
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
		gateway.profile = new ContactDao.ClassificationProfileRow(5, 42, "Dr.", "Ada", "Byron", "Lovelace",
				"Ada", "III", "Ada Lovelace", List.of(new ContactDao.AssignedDefinitionRow(1001, historical, true)),
				List.of(), List.of(new ContactDao.AssignedCredentialRow(1002, credential, 4, false)));

		ClassificationProfile profile = new ContactServiceAdapter(gateway).getClassificationProfile(5, 42).orElseThrow();

		assertEquals("Ada Lovelace", profile.legacyDisplayName());
		assertEquals("III", profile.structuredName().suffix());
		assertEquals(91, profile.contactTypes().get(0).definition().id());
		assertTrue(profile.contactTypes().get(0).historical());
		assertEquals("#112233", profile.contactTypes().get(0).definition().color());
		assertEquals("Doctor of Medicine", profile.credentials().get(0).definition().name());
		assertEquals("MD", profile.credentials().get(0).definition().abbreviation());
		assertEquals("#445566", profile.credentials().get(0).definition().color());
		assertEquals(4, profile.credentials().get(0).displayOrder());
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

		@SuppressWarnings("unused")
		private static ContactDao.ContactDetailRow detailRow() {
			return new ContactDao.ContactDetailRow(1, 42, "Ada Lovelace", "Ada", "Lovelace",
					"Ada Lovelace", "ada@example.com", "555", "123 Main", LocalDate.of(1815, 12, 10),
					"", false, true, false, Instant.now());
		}
	}
}
