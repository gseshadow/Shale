package com.shale.ui.services;

import com.shale.core.model.Organization;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.CalendarEventDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final int USER_PHONE_WEIGHT = 225;
	private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

	private final CaseDao caseDao;
	private final CaseSummaryDao caseSummaryDao;
	private final ContactDao contactDao;
	private final OrganizationDao organizationDao;
	private final UserDao userDao;
	private final TaskDao taskDao;
	private final CalendarEventDao calendarEventDao;

	public SearchService(CaseDao caseDao, CaseSummaryDao caseSummaryDao, ContactDao contactDao, OrganizationDao organizationDao, UserDao userDao, TaskDao taskDao, CalendarEventDao calendarEventDao) {
		this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
		this.caseSummaryDao = Objects.requireNonNull(caseSummaryDao, "caseSummaryDao");
		this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
		this.organizationDao = Objects.requireNonNull(organizationDao, "organizationDao");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
		this.calendarEventDao = Objects.requireNonNull(calendarEventDao, "calendarEventDao");
	}

	public SearchResults searchAll(int shaleClientId, Integer currentUserId, String query, boolean includeDeletedCases) {
		SearchQuery searchQuery = SearchQuery.from(query);
		if (searchQuery.normalizedText().isBlank()) {
			return SearchResults.empty(searchQuery.rawQuery());
		}
		LOG.info("Global search start userId={} shaleClientId={} providers={}", currentUserId, shaleClientId, "cases,deletedCases,contacts,organizations,users,tasks,calendarEvents");
		List<ProviderFailure> failures = new java.util.ArrayList<>();
		List<CaseSummaryDao.SearchCaseRow> cases = provider("cases", failures, () -> sortResults(caseSummaryDao.searchActiveByName(shaleClientId, searchQuery.rawQuery()), row -> scoreCase(row, searchQuery), row -> row.summary().caseName(), row -> Long.toString(row.summary().caseId())));
		List<CaseSummaryDao.DeletedCaseRow> deletedCases = includeDeletedCases ? provider("deletedCases", failures, () -> sortResults(caseSummaryDao.searchDeletedByName(shaleClientId, searchQuery.rawQuery()), row -> scoreDeletedCase(row, searchQuery), row -> row.summary().caseName(), row -> Long.toString(row.summary().caseId()))) : List.of();
		List<ContactDao.DirectoryContactRow> contacts = provider("contacts", failures, () -> sortResults(contactDao.searchContacts(shaleClientId, searchQuery.rawQuery()), row -> scoreContact(row, searchQuery), ContactDao.DirectoryContactRow::displayName, row -> Integer.toString(row.id())));
		List<Organization> organizations = provider("organizations", failures, () -> sortResults(organizationDao.searchOrganizations(searchQuery.rawQuery()), row -> scoreOrganization(row, searchQuery), Organization::getName, row -> Integer.toString(Objects.requireNonNullElse(row.getId(), 0))));
		List<UserDao.DirectoryUserRow> users = provider("users", failures, () -> sortResults(userDao.searchUsers(shaleClientId, searchQuery.rawQuery()), row -> scoreUser(row, searchQuery), UserDao.DirectoryUserRow::displayName, row -> Integer.toString(row.id())));
		List<TaskDao.GlobalSearchTaskRow> tasks = provider("tasks", failures, () -> sortResults(taskDao.searchTasks(shaleClientId, searchQuery.rawQuery()), row -> weightedTextScore(searchQuery, row.title(), CASE_NAME_WEIGHT), TaskDao.GlobalSearchTaskRow::title, row -> Long.toString(row.taskId())));
		List<CalendarEventDao.GlobalSearchCalendarEventRow> calendarEvents = provider("calendarEvents", failures, () -> sortResults(calendarEventDao.searchCalendarEvents(shaleClientId, searchQuery.rawQuery()), row -> weightedTextScore(searchQuery, row.title(), CASE_NAME_WEIGHT), CalendarEventDao.GlobalSearchCalendarEventRow::title, row -> Integer.toString(row.calendarEventId())));
		return new SearchResults(searchQuery.rawQuery(), cases, deletedCases, contacts, organizations, users, tasks, calendarEvents, failures);
	}

	private static <T> List<T> provider(String provider, List<ProviderFailure> failures, java.util.function.Supplier<List<T>> loader) {
		try {
			LOG.info("Global search provider start provider={}", provider);
			List<T> rows = loader.get();
			LOG.info("Global search provider done provider={} rows={}", provider, rows == null ? 0 : rows.size());
			return rows == null ? List.of() : rows;
		} catch (RuntimeException ex) {
			LOG.error("Global search provider failed provider={} exceptionClass={} message={}", provider, ex.getClass().getName(), ex.getMessage(), ex);
			failures.add(new ProviderFailure(provider, ex.getClass().getName(), ex.getMessage()));
			return List.of();
		}
	}

	private static int scoreCase(CaseSummaryDao.SearchCaseRow row, SearchQuery query) {
		return weightedTextScore(query, row == null ? null : row.summary().caseName(), CASE_NAME_WEIGHT);
	}

	private static int scoreDeletedCase(CaseSummaryDao.DeletedCaseRow row, SearchQuery query) {
		return weightedTextScore(query, row == null ? null : row.summary().caseName(), CASE_NAME_WEIGHT);
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
				weightedTextScore(query, row.email(), USER_EMAIL_WEIGHT),
				weightedPhoneScore(query, row.phone(), USER_PHONE_WEIGHT));
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
			List<CaseSummaryDao.SearchCaseRow> cases,
			List<CaseSummaryDao.DeletedCaseRow> deletedCases,
			List<ContactDao.DirectoryContactRow> contacts,
			List<Organization> organizations,
			List<UserDao.DirectoryUserRow> users,
			List<TaskDao.GlobalSearchTaskRow> tasks,
			List<CalendarEventDao.GlobalSearchCalendarEventRow> calendarEvents,
			List<ProviderFailure> failures) {
		public SearchResults {
			query = query == null ? "" : query;
			cases = List.copyOf(cases == null ? List.of() : cases);
			deletedCases = List.copyOf(deletedCases == null ? List.of() : deletedCases);
			contacts = List.copyOf(contacts == null ? List.of() : contacts);
			organizations = List.copyOf(organizations == null ? List.of() : organizations);
			users = List.copyOf(users == null ? List.of() : users);
			tasks = List.copyOf(tasks == null ? List.of() : tasks);
			calendarEvents = List.copyOf(calendarEvents == null ? List.of() : calendarEvents);
			failures = List.copyOf(failures == null ? List.of() : failures);
		}

		public boolean hasFailures() { return !failures.isEmpty(); }
		public boolean hasAnyResults() { return !(cases.isEmpty() && deletedCases.isEmpty() && contacts.isEmpty() && organizations.isEmpty() && users.isEmpty() && tasks.isEmpty() && calendarEvents.isEmpty()); }

		public static SearchResults empty(String query) {
			return new SearchResults(query, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
	}
	public record ProviderFailure(String provider, String exceptionClass, String message) { }
}
