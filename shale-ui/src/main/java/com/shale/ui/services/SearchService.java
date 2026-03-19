package com.shale.ui.services;

import com.shale.core.model.Organization;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;

import java.util.List;
import java.util.Objects;

public final class SearchService {

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

	public SearchResults searchAllByName(int shaleClientId, String query) {
		String normalizedQuery = normalizeQuery(query);
		if (normalizedQuery.isBlank()) {
			return SearchResults.empty(normalizedQuery);
		}

		return new SearchResults(
			normalizedQuery,
			caseDao.searchCasesByName(normalizedQuery),
			contactDao.searchContactsByName(shaleClientId, normalizedQuery),
			organizationDao.searchOrganizationsByName(normalizedQuery),
			userDao.searchUsersByName(shaleClientId, normalizedQuery));
	}

	public List<CaseDao.CaseRow> searchCasesByName(String query) {
		String normalizedQuery = normalizeQuery(query);
		return normalizedQuery.isBlank() ? List.of() : caseDao.searchCasesByName(normalizedQuery);
	}

	public List<ContactDao.DirectoryContactRow> searchContactsByName(int shaleClientId, String query) {
		String normalizedQuery = normalizeQuery(query);
		return normalizedQuery.isBlank() ? List.of() : contactDao.searchContactsByName(shaleClientId, normalizedQuery);
	}

	public List<Organization> searchOrganizationsByName(String query) {
		String normalizedQuery = normalizeQuery(query);
		return normalizedQuery.isBlank() ? List.of() : organizationDao.searchOrganizationsByName(normalizedQuery);
	}

	public List<UserDao.DirectoryUserRow> searchUsersByName(int shaleClientId, String query) {
		String normalizedQuery = normalizeQuery(query);
		return normalizedQuery.isBlank() ? List.of() : userDao.searchUsersByName(shaleClientId, normalizedQuery);
	}

	private static String normalizeQuery(String query) {
		if (query == null) {
			return "";
		}
		return query.trim().toLowerCase(java.util.Locale.ROOT);
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
