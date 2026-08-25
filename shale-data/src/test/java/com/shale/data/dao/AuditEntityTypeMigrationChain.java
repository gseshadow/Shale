package com.shale.data.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Chronological contract for immutable audit allowlist migrations. */
final class AuditEntityTypeMigrationChain {
    static final Path AUGUST_14 = Path.of("..", "docs", "sql",
            "2026-08-14_entity_action_audit_entity_type_constraint_case_status.sql");
    static final Path PHASE_1C = Path.of("..", "docs", "sql",
            "2026-08-25_contacts_phase1c_audit_allowlist.sql");
    static final Set<String> HISTORICAL_AT_AUGUST_14 = Set.of(
            "CASE", "CASE_STATUS", "LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE",
            "MATERIAL_REQUEST", "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "CASE_DATE",
            "CASE_DATE_TYPE", "CALENDAR_EVENT", "CASE_DATE_ROLE_MAPPING",
            "CALENDAR_CASE_DATE_TYPE_MAPPING", "FORM_CONFIGURATION", "USER");
    static final Set<String> PHASE_1C_ADDITIONS = Set.of(
            "CONTACT_TYPE", "SPECIALTY", "CREDENTIAL_DEFINITION",
            "CONTACT_CONTACT_TYPE", "CONTACT_SPECIALTY", "CONTACT_CREDENTIAL");

    static String read(Path path) throws Exception { return Files.readString(path); }

    static Set<String> declaredAllowlist(String sql) {
        int start = sql.indexOf("INSERT @Allowed (Value) VALUES");
        if (start < 0) throw new AssertionError("missing authoritative @Allowed declaration");
        int end = sql.indexOf(';', start);
        if (end < 0) throw new AssertionError("unterminated authoritative @Allowed declaration");
        var matcher = Pattern.compile("\\('([A-Z_]+)'\\)").matcher(sql.substring(start, end));
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) values.add(matcher.group(1));
        return Set.copyOf(values);
    }

    private AuditEntityTypeMigrationChain() { }
}
