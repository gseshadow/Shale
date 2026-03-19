package com.shale.ui.services;

import com.shale.core.model.Organization;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class SearchService {

	private static final int TEXT_EXACT_SCORE = 400;
	private static final int TEXT_STARTS_WITH_SCORE = 300;
	private static final int TEXT_WORD_BOUNDARY_SCORE = 200;
	private static final int TEXT_CONTAINS_SCORE = 100;

	private static final int PHONE_EXACT_SCORE = 350;
	private static final int PHONE_STARTS_WITH_SCORE = 250;
	private static final int PHONE_CONTAINS_SCORE = 150;

	private static final int CASE_NAME_WEIGHT = 500;
	private static final int CONTACT_FULL_NAME_WEIGHT = 500;
	private static final int CONTACT_NAME_PART_WEIGHT = 470;
	private static final int CONTACT_EMAIL_WEIGHT = 250;
	private static final int CONTACT_PHONE_WEIGHT = 225;
	private static final int ORGANIZATION_NAME_WEIGHT = 500;
	private static final int ORGANIZATION_EMAIL_WEIGHT = 240;
	private static final int ORGANIZATION_PHONE_WEIGHT = 220;
	private static final int USER_FULL_NAME_WEIGHT = 500;
	private static final int USER_NAME_PART_WEIGHT = 470;
	private static final int USER_EMAIL_WEIGHT = 250;

	private final CaseDao caseDao;
	private final ContactDao contactDao;
	private final OrganizationDao organizationDao;
	private final UserDao userDao;

	public SearchService(CaseDao caseDao, ContactDao contactDao, OrganizationDao organizationDao, UserDao userDao) {
		this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
		this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
		this.organizationDao = Objects.requireNonNull(organizationDao, "organizationDao");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
	}

	public SearchResults searchAll(int shaleClientId, String query) {
		SearchQuery searchQuery = SearchQuery.from(query);
		if (searchQuery.normalizedText().isBlank()) {
			return SearchResults.empty(searchQuery.rawQuery());
		}

		List<CaseDao.CaseRow> cases = sortResults(
				caseDao.searchCasesByName(searchQuery.rawQuery()),
				row -> scoreCase(row, searchQuery),
				CaseDao.CaseRow::name,
				row -> Long.toString(row.id()));
		List<ContactDao.DirectoryContactRow> contacts = sortResults(
				contactDao.searchContacts(shaleClientId, searchQuery.rawQuery()),
				row -> scoreContact(row, searchQuery),
				ContactDao.DirectoryContactRow::displayName,
				row -> Integer.toString(row.id()));
		List<Organization> organizations = sortResults(
				organizationDao.searchOrganizations(searchQuery.rawQuery()),
				row -> scoreOrganization(row, searchQuery),
				Organization::getName,
				row -> Integer.toString(Objects.requireNonNullElse(row.getId(), 0)));
		List<UserDao.DirectoryUserRow> users = sortResults(
				userDao.searchUsers(shaleClientId, searchQuery.rawQuery()),
				row -> scoreUser(row, searchQuery),
				UserDao.DirectoryUserRow::displayName,
				row -> Integer.toString(row.id()));

		return new SearchResults(searchQuery.rawQuery(), cases, contacts, organizations, users);
	}

	private static int scoreCase(CaseDao.CaseRow row, SearchQuery query) {
		return weightedTextScore(query, row == null ? null : row.name(), CASE_NAME_WEIGHT);
	}

	private static int scoreContact(ContactDao.DirectoryContactRow row, SearchQuery query) {
		if (row == null) {
			return 0;
		}
		String fullName = preferCombinedName(row.firstName(), row.lastName(), row.displayName());
		return maxScore(
				weightedTextScore(query, fullName, CONTACT_FULL_NAME_WEIGHT),
				weightedTextScore(query, row.firstName(), CONTACT_NAME_PART_WEIGHT),
				weightedTextScore(query, row.lastName(), CONTACT_NAME_PART_WEIGHT),
				weightedTextScore(query, row.email(), CONTACT_EMAIL_WEIGHT),
				weightedPhoneScore(query, row.phone(), CONTACT_PHONE_WEIGHT));
	}

	private static int scoreOrganization(Organization organization, SearchQuery query) {
		if (organization == null) {
			return 0;
		}
		return maxScore(
				weightedTextScore(query, organization.getName(), ORGANIZATION_NAME_WEIGHT),
				weightedTextScore(query, organization.getEmail(), ORGANIZATION_EMAIL_WEIGHT),
				weightedPhoneScore(query, organization.getPhone(), ORGANIZATION_PHONE_WEIGHT),
				weightedPhoneScore(query, organization.getFax(), ORGANIZATION_PHONE_WEIGHT));
	}

	private static int scoreUser(UserDao.DirectoryUserRow row, SearchQuery query) {
		if (row == null) {
			return 0;
		}
		String fullName = preferCombinedName(row.firstName(), row.lastName(), row.displayName());
		return maxScore(
				weightedTextScore(query, fullName, USER_FULL_NAME_WEIGHT),
				weightedTextScore(query, row.firstName(), USER_NAME_PART_WEIGHT),
				weightedTextScore(query, row.lastName(), USER_NAME_PART_WEIGHT),
				weightedTextScore(query, row.email(), USER_EMAIL_WEIGHT));
	}

	private static int weightedTextScore(SearchQuery query, String candidate, int fieldWeight) {
		int matchScore = scoreTextMatch(query.normalizedText(), candidate);
		return matchScore <= 0 ? 0 : fieldWeight + matchScore;
	}

	private static int weightedPhoneScore(SearchQuery query, String candidate, int fieldWeight) {
		int matchScore = scorePhoneMatch(query.normalizedPhoneDigits(), candidate);
		return matchScore <= 0 ? 0 : fieldWeight + matchScore;
	}

	static String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	static String normalizePhoneDigits(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isDigit(c)) {
				digits.append(c);
			}
		}
		return digits.toString();
	}

	static int scoreTextMatch(String normalizedQuery, String candidate) {
		String normalizedCandidate = normalizeText(candidate);
		if (normalizedQuery == null || normalizedQuery.isBlank() || normalizedCandidate.isBlank()) {
			return 0;
		}
		if (normalizedCandidate.equals(normalizedQuery)) {
			return TEXT_EXACT_SCORE;
		}
		if (normalizedCandidate.startsWith(normalizedQuery)) {
			return TEXT_STARTS_WITH_SCORE;
		}
		if (hasWordBoundaryStartsWith(normalizedCandidate, normalizedQuery)) {
			return TEXT_WORD_BOUNDARY_SCORE;
		}
		if (normalizedCandidate.contains(normalizedQuery)) {
			return TEXT_CONTAINS_SCORE;
		}
		return 0;
	}

	static int scorePhoneMatch(String normalizedPhoneQuery, String candidate) {
		String normalizedCandidate = normalizePhoneDigits(candidate);
		if (normalizedPhoneQuery == null || normalizedPhoneQuery.isBlank() || normalizedCandidate.isBlank()) {
			return 0;
		}
		if (normalizedCandidate.equals(normalizedPhoneQuery)) {
			return PHONE_EXACT_SCORE;
		}
		if (normalizedCandidate.startsWith(normalizedPhoneQuery)) {
			return PHONE_STARTS_WITH_SCORE;
		}
		if (normalizedCandidate.contains(normalizedPhoneQuery)) {
			return PHONE_CONTAINS_SCORE;
		}
		return 0;
	}

	private static boolean hasWordBoundaryStartsWith(String normalizedCandidate, String normalizedQuery) {
		if (normalizedCandidate.isBlank() || normalizedQuery.isBlank()) {
			return false;
		}
		String[] tokens = normalizedCandidate.split("[^a-z0-9]+");
		for (String token : tokens) {
			if (!token.isBlank() && token.startsWith(normalizedQuery)) {
				return true;
			}
		}
		return false;
	}

	private static <T> List<T> sortResults(List<T> items,
			ToIntFunction<T> scorer,
			Function<T, String> displayName,
			Function<T, String> stableId) {
		Comparator<T> comparator = Comparator
				.<T>comparingInt(item -> scorer.applyAsInt(item))
				.reversed()
				.thenComparing(item -> safeDisplayName(displayName.apply(item)), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(item -> Objects.requireNonNullElse(stableId.apply(item), ""), String.CASE_INSENSITIVE_ORDER);
		return items.stream()
				.filter(Objects::nonNull)
				.sorted(comparator)
				.toList();
	}

	private static String safeDisplayName(String value) {
		String normalized = value == null ? "" : value.trim();
		return normalized.isBlank() ? "~" : normalized;
	}

	private static String preferCombinedName(String firstName, String lastName, String fallback) {
		String first = firstName == null ? "" : firstName.trim();
		String last = lastName == null ? "" : lastName.trim();
		String combined = (first + " " + last).trim();
		return combined.isBlank() ? fallback : combined;
	}

	private static int maxScore(int... scores) {
		int best = 0;
		for (int score : scores) {
			if (score > best) {
				best = score;
			}
		}
		return best;
	}

	private record SearchQuery(String rawQuery, String normalizedText, String normalizedPhoneDigits) {
		private static SearchQuery from(String query) {
			String raw = query == null ? "" : query.trim();
			return new SearchQuery(raw, normalizeText(raw), normalizePhoneDigits(raw));
		}
	}

	public record SearchResults(
			String query,
			List<CaseDao.CaseRow> cases,
			List<ContactDao.DirectoryContactRow> contacts,
			List<Organization> organizations,
			List<UserDao.DirectoryUserRow> users) {
		public SearchResults {
			query = query == null ? "" : query;
			cases = List.copyOf(cases == null ? List.of() : cases);
			contacts = List.copyOf(contacts == null ? List.of() : contacts);
			organizations = List.copyOf(organizations == null ? List.of() : organizations);
			users = List.copyOf(users == null ? List.of() : users);
		}

		public static SearchResults empty(String query) {
			return new SearchResults(query, List.of(), List.of(), List.of(), List.of());
		}
	}
}
