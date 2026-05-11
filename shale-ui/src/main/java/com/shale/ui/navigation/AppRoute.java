package com.shale.ui.navigation;

import java.util.Locale;
import java.util.Objects;

public record AppRoute(RouteType type, Integer entityId, String sectionKey, String searchQuery) {

    public enum RouteType {
        MY_SHALE,
        CASES_LIST,
        CONTACTS_LIST,
        ORGANIZATIONS_LIST,
        TEAM_LIST,
        CALENDAR,
        SETTINGS,
        SEARCH,
        CASE_PROFILE,
        CONTACT_PROFILE,
        ORGANIZATION_PROFILE,
        USER_PROFILE
    }

    public AppRoute {
        Objects.requireNonNull(type, "type");
        sectionKey = normalizeSectionKey(sectionKey);
        searchQuery = normalizeSearchQuery(searchQuery);
    }

    public static AppRoute myShale() {
        return new AppRoute(RouteType.MY_SHALE, null, null, null);
    }

    public static AppRoute casesList() {
        return new AppRoute(RouteType.CASES_LIST, null, null, null);
    }

    public static AppRoute contactsList() {
        return new AppRoute(RouteType.CONTACTS_LIST, null, null, null);
    }

    public static AppRoute organizationsList() {
        return new AppRoute(RouteType.ORGANIZATIONS_LIST, null, null, null);
    }

    public static AppRoute teamList() {
        return new AppRoute(RouteType.TEAM_LIST, null, null, null);
    }

    public static AppRoute calendar() {
        return new AppRoute(RouteType.CALENDAR, null, null, null);
    }

    public static AppRoute settings() {
        return new AppRoute(RouteType.SETTINGS, null, null, null);
    }

    public static AppRoute search(String query) {
        return new AppRoute(RouteType.SEARCH, null, null, query);
    }

    public static AppRoute caseProfile(Integer caseId, String sectionKey) {
        return new AppRoute(RouteType.CASE_PROFILE, caseId, sectionKey, null);
    }

    public static AppRoute contactProfile(Integer contactId) {
        return new AppRoute(RouteType.CONTACT_PROFILE, contactId, null, null);
    }

    public static AppRoute organizationProfile(Integer organizationId) {
        return new AppRoute(RouteType.ORGANIZATION_PROFILE, organizationId, null, null);
    }

    public static AppRoute userProfile(Integer userId) {
        return new AppRoute(RouteType.USER_PROFILE, userId, null, null);
    }

    private static String normalizeSectionKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSearchQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
