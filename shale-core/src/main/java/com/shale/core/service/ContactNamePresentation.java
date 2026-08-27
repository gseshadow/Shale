package com.shale.core.service;

import java.util.Comparator;
import java.util.List;

import com.shale.core.service.ContactServicePort.AssignedCredential;
import com.shale.core.service.ContactServicePort.StructuredName;

/** Authoritative, non-persisted presentation of a Contact's structured name and credentials. */
public final class ContactNamePresentation {
	private ContactNamePresentation() {}

	public static String compose(StructuredName name, String legacyDisplayName,
			List<AssignedCredential> credentials) {
		String structured = name == null ? "" : join(name.prefix(), name.firstName(), name.middleName(),
				name.lastName(), name.suffix());
		String result = structured.isBlank() ? clean(legacyDisplayName) : structured;
		if (credentials == null || credentials.isEmpty()) return result;

		List<String> abbreviations = credentials.stream()
				.filter(c -> c != null && !c.historical() && c.definition() != null)
				.sorted(Comparator.comparingInt(AssignedCredential::displayOrder))
				.map(c -> clean(c.definition().abbreviation()))
				.filter(s -> !s.isEmpty())
				.toList();
		boolean appendedCredential = false;
		for (String abbreviation : abbreviations) {
			if (endsWithCredential(result, abbreviation)) {
				appendedCredential = true;
				continue;
			}
			result = result.isEmpty() ? abbreviation : result + (appendedCredential ? ", " : " ") + abbreviation;
			appendedCredential = true;
		}
		return result;
	}

	private static String join(String... parts) {
		return java.util.Arrays.stream(parts).map(ContactNamePresentation::clean)
				.filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.joining(" "));
	}

	private static boolean endsWithCredential(String value, String abbreviation) {
		String v = clean(value);
		return v.equalsIgnoreCase(abbreviation)
				|| (v.length() > abbreviation.length()
				&& v.regionMatches(true, v.length() - abbreviation.length(), abbreviation, 0, abbreviation.length())
				&& Character.isWhitespace(v.charAt(v.length() - abbreviation.length() - 1)));
	}

	private static String clean(String value) { return value == null ? "" : value.trim(); }
}
