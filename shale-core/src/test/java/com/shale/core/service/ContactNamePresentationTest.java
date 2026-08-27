package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.ContactServicePort.*;

class ContactNamePresentationTest {
	private static final StructuredName NAME = new StructuredName(null, "Example", null, "Doctor", null, null);

	@Test void composesWithoutCredentials() { assertEquals("Example Doctor", compose(NAME)); }
	@Test void appendsOneCredentialByAbbreviation() { assertEquals("Example Doctor M.D.", compose(NAME, credential(1, 9, "Medical Doctor", "M.D.", false))); }
	@Test void appendsMultipleCredentialsInAuthoritativeOrder() {
		assertEquals("Example Doctor M.D., Ph.D.", compose(NAME, credential(2, 20, "Doctor of Philosophy", "Ph.D.", false), credential(1, 10, "Medical Doctor", "M.D.", false)));
	}
	@Test void includesPrefixAndSuffixBeforeCredentials() {
		var name = new StructuredName("Dr.", "Example", "Q.", "Doctor", null, "Jr.");
		assertEquals("Dr. Example Q. Doctor Jr. M.D.", compose(name, credential(1, 0, "Medical Doctor", "M.D.", false)));
	}
	@Test void ignoresRemovedOrInactiveCredentials() { assertEquals("Example Doctor", compose(NAME, credential(1, 0, "Medical Doctor", "M.D.", true))); }
	@Test void ignoresBlankAbbreviations() { assertEquals("Example Doctor", compose(NAME, credential(1, 0, "Medical Doctor", "  ", false))); }
	@Test void suppressesCredentialAlreadyAtEndOfLegacyDisplayName() {
		assertEquals("Example Doctor M.D.", ContactNamePresentation.compose(null, "Example Doctor M.D.", List.of(credential(1, 0, "Medical Doctor", "M.D.", false))));
	}

	private static String compose(StructuredName name, AssignedCredential... credentials) {
		return ContactNamePresentation.compose(name, "Legacy compatibility value", List.of(credentials));
	}
	private static AssignedCredential credential(long id, int order, String descriptiveName, String abbreviation, boolean historical) {
		return new AssignedCredential(id, new CredentialDefinition((int) id, "key" + id, descriptiveName, abbreviation, null, null, order), order, historical, null);
	}
}
