package com.shale.ui.services;

/**
 * Tenant-scoped live-update invalidation names and safe patch builders.
 * Payloads intentionally contain stable identifiers only; never add URLs, titles,
 * descriptions, notes, Contact PII, RowVer, raw metadata, SQL, exceptions, or DTOs.
 */
public final class LiveUpdateEvents {
    public static final String ENTITY_CASE_LINK = "CaseLink";
    public static final String ENTITY_CASE_LINK_SHARE = "CaseLinkShare";
    public static final String ENTITY_LINK_TYPE = "LinkType";
    public static final String ENTITY_AUDIT_ACTIVITY = "EntityAuditActivity";
    /** PHI-free invalidation for any committed dbo.CaseDates occurrence mutation. */
    public static final String ENTITY_CASE_DATES = "CaseDates";
    public static final String ENTITY_CASE_DATE_TYPES = "CaseDateTypes";

    public static final String CHANGE_CREATED = "CREATED";
    public static final String CHANGE_UPDATED = "UPDATED";
    public static final String CHANGE_DELETED = "DELETED";
    public static final String CHANGE_PRIMARY_CHANGED = "PRIMARY_CHANGED";
    public static final String CHANGE_REORDERED = "REORDERED";
    public static final String CHANGE_ADDED = "ADDED";
    public static final String CHANGE_REMOVED = "REMOVED";
    public static final String CHANGE_SHARED = CHANGE_ADDED;
    public static final String CHANGE_SHARE_UPDATED = CHANGE_UPDATED;
    public static final String CHANGE_UNSHARED = CHANGE_REMOVED;
    public static final String CHANGE_ACTIVATED = "ACTIVATED";
    public static final String CHANGE_DEACTIVATED = "DEACTIVATED";
    public static final String CHANGE_OVERRIDE_RESET = "OVERRIDE_RESET";
    public static final String CHANGE_ACTIVITY_ADDED = "ACTIVITY_ADDED";

    private LiveUpdateEvents() {}

    public static String caseLinkPatch(long caseId, Long caseLinkId, Long externalLinkId, Integer linkTypeId, String change) {
        StringBuilder json = new StringBuilder("{");
        append(json, "caseId", caseId);
        append(json, "caseLinkId", caseLinkId);
        append(json, "externalLinkId", externalLinkId);
        append(json, "linkTypeId", linkTypeId);
        append(json, "change", change);
        return json.append('}').toString();
    }

    public static String caseLinkSharePatch(long caseId, long caseLinkId, Long shareId, Integer contactId, String change) {
        StringBuilder json = new StringBuilder("{");
        append(json, "caseId", caseId);
        append(json, "caseLinkId", caseLinkId);
        append(json, "caseLinkShareId", shareId);
        append(json, "contactId", contactId);
        append(json, "change", change);
        return json.append('}').toString();
    }

    public static String linkTypePatch(int linkTypeId, String change) {
        StringBuilder json = new StringBuilder("{");
        append(json, "linkTypeId", linkTypeId);
        append(json, "change", change);
        return json.append('}').toString();
    }

    public static String auditActivityPatch(Long entityActionAuditLogId) {
        StringBuilder json = new StringBuilder("{");
        append(json, "entityActionAuditLogId", entityActionAuditLogId);
        append(json, "change", CHANGE_ACTIVITY_ADDED);
        return json.append('}').toString();
    }

    public static String caseDatesPatch(long caseId, String change) {
        StringBuilder json = new StringBuilder("{");
        append(json, "caseId", caseId);
        append(json, "change", change);
        return json.append('}').toString();
    }

    private static void append(StringBuilder json, String key, Object value) {
        if (value == null) return;
        if (json.length() > 1) json.append(',');
        json.append('"').append(key).append('"').append(':');
        if (value instanceof Number || value instanceof Boolean) json.append(value);
        else json.append('"').append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }
}
