package com.shale.data.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Applies ranking and a server-side bound to the existing universal-search SELECT.
 * The supplied projection identifiers are DAO-owned constants, never user input. */
public record SuggestionBounds(int limit, Long entityId) {
    public SuggestionBounds(int limit) { this(limit, null); }
    public SuggestionBounds {
        if (limit < 1 || limit > 5) throw new IllegalArgumentException("Suggestion limit must be 1..5");
        if (entityId != null && entityId <= 0) throw new IllegalArgumentException("Invalid entity identity");
    }

    public String sql(String original, String name, String id, List<String> text, List<String> phones) {
        int order = original.lastIndexOf("ORDER BY");
        if (order < 0) throw new IllegalArgumentException("Search must declare a stable outer order");
        String select = original.substring(0, order).strip()
                .replaceFirst("(?i)SELECT TOP \\(100\\)", "SELECT");
        String exact = predicates(text, phones, false);
        String prefix = predicates(text, phones, true);
        return "SELECT * FROM (" + select + ") suggestion_rows"
                + (entityId == null ? "" : " WHERE " + id + " = ?") + " ORDER BY CASE WHEN (" + exact
                + ") THEN 0 WHEN (" + prefix + ") THEN 1 ELSE 2 END, LOWER(COALESCE("
                + name + ",'')), " + id + " OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
    }

    public void bind(PreparedStatement ps, int next, String query, List<String> text, List<String> phones)
            throws SQLException {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String digits = normalized.replaceAll("[^0-9]", "");
        if (entityId != null) ps.setLong(next++, entityId);
        for (boolean prefix : new boolean[]{false, true}) {
            String value = prefix ? CaseSummaryDao.escapeLike(normalized) + "%" : normalized;
            for (String ignored : text) ps.setString(next++, value);
            for (String ignored : phones) {
                ps.setString(next++, digits);
                ps.setString(next++, prefix ? digits + "%" : digits);
            }
        }
        ps.setInt(next, limit);
        ps.setQueryTimeout(5);
    }

    private static String predicates(List<String> text, List<String> phones, boolean prefix) {
        String operator = prefix ? " LIKE ?" : " = ?";
        var values = new java.util.ArrayList<String>();
        text.forEach(field -> values.add("LOWER(LTRIM(RTRIM(COALESCE(" + field + ",''))))" + operator));
        phones.forEach(field -> {
            String normalized = "COALESCE(" + field + ",'')";
            for (String character : List.of(" ", "-", "(", ")", ".", "+", "/"))
                normalized = "REPLACE(" + normalized + ",'" + character + "','')";
            values.add("(? <> '' AND " + normalized + operator + ")");
        });
        return values.stream().collect(Collectors.joining(" OR "));
    }
}
