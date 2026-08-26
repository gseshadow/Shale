package com.shale.data.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Chronological contract for immutable audit allowlist migrations. */
final class AuditEntityTypeMigrationChain {
    static final Path AUGUST_14 = Path.of("..", "docs", "sql",
            "2026-08-14_entity_action_audit_entity_type_constraint_case_status.sql");
    static final Path USER_MANAGEMENT = Path.of("..", "docs", "sql",
            "2026-08-03_users_management_completion.sql");
    static final Path PHASE_1C = Path.of("..", "docs", "sql",
            "2026-08-25_contacts_phase1c_audit_allowlist.sql");
    static final Path PHASE_2B = Path.of("..", "docs", "sql",
            "2026-08-26_contacts_phase2b_audit_allowlist.sql");
    static final Path CURRENT = PHASE_2B;
    static final Set<String> USER_MANAGEMENT_SEEDED = Set.of(
            "CASE", "LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE", "MATERIAL_REQUEST",
            "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "USER", "CASE_DATE", "CALENDAR_EVENT",
            "CASE_DATE_ROLE_MAPPING", "CALENDAR_CASE_DATE_TYPE_MAPPING", "FORM_CONFIGURATION");
    static final Set<String> HISTORICAL_AT_AUGUST_14 = Set.of(
            "CASE", "CASE_STATUS", "LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE",
            "MATERIAL_REQUEST", "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "CASE_DATE",
            "CASE_DATE_TYPE", "CALENDAR_EVENT", "CASE_DATE_ROLE_MAPPING",
            "CALENDAR_CASE_DATE_TYPE_MAPPING", "FORM_CONFIGURATION", "USER");
    static final Set<String> PHASE_1C_ADDITIONS = Set.of(
            "CONTACT_TYPE", "SPECIALTY", "CREDENTIAL_DEFINITION",
            "CONTACT_CONTACT_TYPE", "CONTACT_SPECIALTY", "CONTACT_CREDENTIAL");
    static final Set<String> PHASE_1C_VOCABULARY = union(HISTORICAL_AT_AUGUST_14, PHASE_1C_ADDITIONS);

    static String read(Path path) throws Exception { return Files.readString(path); }

    static Set<String> declaredAllowlist(String sql) {
        return Set.copyOf(declaredAllowlistTokens(sql));
    }

    static List<String> declaredAllowlistTokens(String sql) {
        int start = sql.indexOf("INSERT @Allowed (Value) VALUES");
        if (start < 0) throw new AssertionError("missing authoritative @Allowed declaration");
        int end = sql.indexOf(';', start);
        if (end < 0) throw new AssertionError("unterminated authoritative @Allowed declaration");
        var matcher = Pattern.compile("\\('([A-Z_]+)'\\)").matcher(sql.substring(start, end));
        var values = new java.util.ArrayList<String>();
        while (matcher.find()) values.add(matcher.group(1));
        return List.copyOf(values);
    }

    static Set<String> historicalSeededAllowlist(String sql) {
        int start = sql.indexOf("INSERT @Allowed(Value) VALUES");
        if (start < 0) throw new AssertionError("missing historical @Allowed declaration");
        int end = sql.indexOf(';', start);
        if (end < 0) throw new AssertionError("unterminated historical @Allowed declaration");
        var matcher = Pattern.compile("\\(N?'([A-Z_]+)'\\)").matcher(sql.substring(start, end));
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) values.add(matcher.group(1));
        return Set.copyOf(values);
    }

    static Set<String> currentlyRequiredVocabulary() {
        Set<String> values = new LinkedHashSet<>();
        for (EntityActionAuditEvent.EntityType entityType : EntityActionAuditEvent.EntityType.values()) {
            values.add(entityType.name());
        }
        // Retained historical token emitted by the retired calendar mapping writer.
        values.add("CALENDAR_CASE_DATE_TYPE_MAPPING");
        return Set.copyOf(values);
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> values = new LinkedHashSet<>();
        for (Set<String> set : sets) values.addAll(set);
        return Set.copyOf(values);
    }

    private AuditEntityTypeMigrationChain() { }
}
