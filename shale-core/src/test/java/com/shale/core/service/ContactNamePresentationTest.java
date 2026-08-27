package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.ContactServicePort.*;

class ContactNamePresentationTest {
	private static final StructuredName STRUCTURED = new StructuredName("Dr.", "Example", null, "Doctor", null, null);

	@Test void noCredentialsPreserveStoredDisplayName() {
		assertEquals("Example Doctor", display("Example Doctor"));
	}

	@Test void baseDisplayNameUsesStructuredFieldsButNotPreferredName() {
		assertEquals("Dr. Jane Mary Smith Jr.", ContactNamePresentation.baseDisplayName(
				new StructuredName(" Dr. ", "Jane", "Mary", "Smith", "Janey", "Jr.")));
	}

	@Test void baseDisplayNameIgnoresBlankComponentsAndNormalizesWhitespace() {
		assertEquals("Jane Smith", ContactNamePresentation.baseDisplayName(
				new StructuredName(" ", " Jane ", null, " Smith ", "Preferred", "")));
	}

	@Test void effectiveDisplayNameUsesAbbreviationsInAssignmentOrder() {
		assertEquals("Jane Smith R.N., B.S.N.", display("Jane Smith",
				credential(2, 20, "Bachelor of Science in Nursing", "B.S.N.", false),
				credential(1, 10, "Registered Nurse", "R.N.", false)));
	}

	@Test void removingCredentialRemovesItsAbbreviation() {
		assertEquals("Example Doctor", display("Example Doctor",
				credential(1, 0, "Medical Doctor", "M.D.", true)));
	}

	@Test void reorderingCredentialsChangesPresentationOrder() {
		assertEquals("Example Doctor Ph.D., M.D.", display("Example Doctor",
				credential(1, 20, "Medical Doctor", "M.D.", false),
				credential(2, 10, "Doctor of Philosophy", "Ph.D.", false)));
	}

	@Test void blankAbbreviationsAreIgnored() {
		assertEquals("Example Doctor", display("Example Doctor", credential(1, 0, "Medical Doctor", " ", false)));
	}

	@Test void legacyDisplayNameEndingInCredentialDoesNotDuplicateIt() {
		assertEquals("Example Doctor M.D.", display("Example Doctor M.D.", credential(1, 0, "Medical Doctor", "M.D.", false)));
	}

	@Test void structuredAndDisplayNameRemainDistinctCompositions() {
		var credentials = List.of(credential(1, 0, "Medical Doctor", "M.D.", false));
		assertEquals("Example Doctor M.D.", ContactNamePresentation.effectiveDisplayName("Example Doctor", credentials));
		assertEquals("Dr. Example Doctor M.D.", ContactNamePresentation.structuredFullName(STRUCTURED, credentials));
	}

	private static String display(String base, AssignedCredential... credentials) {
		return ContactNamePresentation.effectiveDisplayName(base, List.of(credentials));
	}

	private static AssignedCredential credential(long id, int order, String name, String abbreviation, boolean historical) {
		return new AssignedCredential(id, new CredentialDefinition((int) id, "key" + id, name,
				abbreviation, null, null, order), order, historical, null);
	}
}
