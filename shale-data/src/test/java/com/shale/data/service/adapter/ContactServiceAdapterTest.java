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
		ContactDao.DefinitionRow historical = new ContactDao.DefinitionRow(91, "expert", "Expert", null, 2, false, true);
		ContactDao.CredentialDefinitionRow credential = new ContactDao.CredentialDefinitionRow(
				73, "doctor_of_medicine", "Doctor of Medicine", "MD", null, 1, true, false);
		gateway.profile = new ContactDao.ClassificationProfileRow(5, 42, "Dr.", "Ada", "Byron", "Lovelace",
				"Ada", "III", "Ada Lovelace", List.of(new ContactDao.AssignedDefinitionRow(1001, historical, true)),
				List.of(), List.of(new ContactDao.AssignedCredentialRow(1002, credential, 4, false)));

		ClassificationProfile profile = new ContactServiceAdapter(gateway).getClassificationProfile(5, 42).orElseThrow();

		assertEquals("Ada Lovelace", profile.legacyDisplayName());
		assertEquals("III", profile.structuredName().suffix());
		assertEquals(91, profile.contactTypes().get(0).definition().id());
		assertTrue(profile.contactTypes().get(0).historical());
		assertEquals("Doctor of Medicine", profile.credentials().get(0).definition().name());
		assertEquals("MD", profile.credentials().get(0).definition().abbreviation());
		assertEquals(4, profile.credentials().get(0).displayOrder());
	}

	private static final class FakeContactGateway implements ContactServiceAdapter.ContactGateway {
		private final List<ContactDao.DirectoryContactRow> rows;
		private int lastSearchShaleClientId;
		private String lastSearchQuery;
		private int lastDetailContactId;
		private int lastDetailShaleClientId;
		private boolean fullDetailLookupCalled;
		private ContactDao.ClassificationProfileRow profile;

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

		@SuppressWarnings("unused")
		private static ContactDao.ContactDetailRow detailRow() {
			return new ContactDao.ContactDetailRow(1, 42, "Ada Lovelace", "Ada", "Lovelace",
					"Ada Lovelace", "ada@example.com", "555", "123 Main", LocalDate.of(1815, 12, 10),
					"", false, true, false, Instant.now());
		}
	}
}
