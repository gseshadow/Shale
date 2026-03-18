package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContactDao {

    public record DirectoryContactRow(
            int id,
            String displayName,
            String email,
            String phone
    ) {
    }

    public record ContactDetailRow(
            int id,
            int shaleClientId,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phone,
            boolean deleted
    ) {
    }

    private final DbSessionProvider db;

    public ContactDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public List<DirectoryContactRow> listContactsForTenant(int shaleClientId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);

            String phoneColumn = existingColumn(con, "Contacts", List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number"));
            String emailColumn = existingColumn(con, "Contacts", List.of("Email", "EmailPersonal", "email_personal", "email"));
            String deletedColumn = existingColumn(con, "Contacts", List.of("IsDeleted", "is_deleted"));

            String sql = """
                    SELECT
                      c.Id,
                      LTRIM(RTRIM(
                        CASE
                          WHEN (NULLIF(LTRIM(RTRIM(COALESCE(c.FirstName, ''))), '') IS NOT NULL)
                            OR (NULLIF(LTRIM(RTRIM(COALESCE(c.LastName, ''))), '') IS NOT NULL)
                          THEN
                            COALESCE(c.FirstName, '') +
                            CASE WHEN COALESCE(c.FirstName, '') = '' OR COALESCE(c.LastName, '') = '' THEN '' ELSE ' ' END +
                            COALESCE(c.LastName, '')
                          ELSE
                            COALESCE(c.Name, '')
                        END
                      )) AS DisplayName,
                      %s,
                      %s
                    FROM Contacts c
                    WHERE c.ShaleClientId = ?
                      AND NULLIF(LTRIM(RTRIM(
                        CASE
                          WHEN (NULLIF(LTRIM(RTRIM(COALESCE(c.FirstName, ''))), '') IS NOT NULL)
                            OR (NULLIF(LTRIM(RTRIM(COALESCE(c.LastName, ''))), '') IS NOT NULL)
                          THEN
                            COALESCE(c.FirstName, '') +
                            CASE WHEN COALESCE(c.FirstName, '') = '' OR COALESCE(c.LastName, '') = '' THEN '' ELSE ' ' END +
                            COALESCE(c.LastName, '')
                          ELSE
                            COALESCE(c.Name, '')
                        END
                      )), '') IS NOT NULL
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    optionalColumnExpression(emailColumn, "c", "Email"),
                    optionalColumnExpression(phoneColumn, "c", "Phone"),
                    activeFilter(deletedColumn, "c"));

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DirectoryContactRow> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(new DirectoryContactRow(
                                rs.getInt("Id"),
                                rs.getString("DisplayName"),
                                rs.getString("Email"),
                                rs.getString("Phone")));
                    }
                    return out;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list contacts for tenant (clientId=" + shaleClientId + ")", e);
        }
    }

    public ContactDetailRow findById(int contactId, int shaleClientId) {
        if (contactId <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);

            String phoneColumn = existingColumn(con, "Contacts", List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number"));
            String emailColumn = existingColumn(con, "Contacts", List.of("Email", "EmailPersonal", "email_personal", "email"));
            String deletedColumn = existingColumn(con, "Contacts", List.of("IsDeleted", "is_deleted"));

            String sql = """
                    SELECT
                      c.Id,
                      c.ShaleClientId,
                      COALESCE(c.FirstName, '') AS FirstName,
                      COALESCE(c.LastName, '') AS LastName,
                      LTRIM(RTRIM(
                        CASE
                          WHEN (NULLIF(LTRIM(RTRIM(COALESCE(c.FirstName, ''))), '') IS NOT NULL)
                            OR (NULLIF(LTRIM(RTRIM(COALESCE(c.LastName, ''))), '') IS NOT NULL)
                          THEN
                            COALESCE(c.FirstName, '') +
                            CASE WHEN COALESCE(c.FirstName, '') = '' OR COALESCE(c.LastName, '') = '' THEN '' ELSE ' ' END +
                            COALESCE(c.LastName, '')
                          ELSE
                            COALESCE(c.Name, '')
                        END
                      )) AS DisplayName,
                      %s,
                      %s,
                      %s
                    FROM Contacts c
                    WHERE c.Id = ?
                      AND c.ShaleClientId = ?
                    %s;
                    """.formatted(
                    optionalColumnExpression(emailColumn, "c", "Email"),
                    optionalColumnExpression(phoneColumn, "c", "Phone"),
                    deletedExpression(deletedColumn, "c"),
                    activeFilter(deletedColumn, "c"));

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, contactId);
                ps.setInt(2, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new ContactDetailRow(
                            rs.getInt("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getString("FirstName"),
                            rs.getString("LastName"),
                            rs.getString("DisplayName"),
                            rs.getString("Email"),
                            rs.getString("Phone"),
                            rs.getBoolean("IsDeleted"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
        }
    }

    private static void verifyTenantMatchesSession(Connection con, int shaleClientId) throws SQLException {
        int sessionClientId = requireCurrentShaleClientId(con);
        if (sessionClientId != shaleClientId) {
            throw new IllegalStateException("Tenant mismatch. Session clientId=" + sessionClientId + ", requested=" + shaleClientId);
        }
    }

    private static int requireCurrentShaleClientId(Connection con) throws SQLException {
        String sql = "SELECT dbo.fn_CurrentShaleClientId()";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("Current tenant is unavailable");
            }
            int clientId = rs.getInt(1);
            if (clientId <= 0) {
                throw new IllegalStateException("Current tenant is unavailable");
            }
            return clientId;
        }
    }

    private static String existingColumn(Connection con, String table, List<String> candidates) throws SQLException {
        for (String candidate : candidates) {
            if (hasColumn(con, table, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasColumn(Connection con, String table, String column) throws SQLException {
        String sql = """
                SELECT 1
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?;
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String optionalColumnExpression(String column, String alias, String resultAlias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS NVARCHAR(255)) AS " + resultAlias;
        }
        return "NULLIF(LTRIM(RTRIM(" + alias + "." + column + ")), '') AS " + resultAlias;
    }

    private static String deletedExpression(String column, String alias) {
        if (column == null || column.isBlank()) {
            return "CAST(0 AS BIT) AS IsDeleted";
        }
        return "COALESCE(" + alias + "." + column + ", 0) AS IsDeleted";
    }

    private static String activeFilter(String deletedColumn, String alias) {
        if (deletedColumn == null || deletedColumn.isBlank()) {
            return "";
        }
        return "  AND (COALESCE(" + alias + "." + deletedColumn + ", 0) = 0 OR " + alias + "." + deletedColumn + " IS NULL)";
    }
}
