package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shale.core.service.ContactServicePort.ContactDetail;
import com.shale.core.service.ContactServicePort.ContactSummary;
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

	private static final class FakeContactGateway implements ContactServiceAdapter.ContactGateway {
		private final List<ContactDao.DirectoryContactRow> rows;
		private int lastSearchShaleClientId;
		private String lastSearchQuery;

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
		public ContactDao.ContactDetailRow findById(int contactId, int shaleClientId) {
			return null;
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

		@SuppressWarnings("unused")
		private static ContactDao.ContactDetailRow detailRow() {
			return new ContactDao.ContactDetailRow(1, 42, "Ada Lovelace", "Ada", "Lovelace",
					"Ada Lovelace", "ada@example.com", "555", "123 Main", LocalDate.of(1815, 12, 10),
					"", false, true, false, Instant.now());
		}
	}
}
