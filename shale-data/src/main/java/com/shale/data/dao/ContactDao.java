package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
            String name,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phone,
            String addressHome,
            LocalDate dateOfBirth,
            String condition,
            boolean deceased,
            boolean client,
            boolean deleted,
            Instant updatedAt
    ) {
    }

    public record ContactProfileUpdateRequest(
            int contactId,
            int shaleClientId,
            String name,
            String firstName,
            String lastName,
            String email,
            String phone,
            String addressHome,
            LocalDate dateOfBirth,
            String condition,
            boolean deceased,
            boolean client
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

            ContactSchema schema = ContactSchema.load(con);
            String sql = """
                    SELECT
                      c.Id,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.ShaleClientId = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    displayNameExpression(schema, "c"),
                    optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                    optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                    displayNameExpression(schema, "c"),
                    activeFilter(schema.deletedColumn(), "c"));

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

    public ContactDetailRow findById(int contactId) {
        if (contactId <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            return findById(con, contactId, requireCurrentShaleClientId(con));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
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
            return findById(con, contactId, shaleClientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
        }
    }

    public boolean updateBasicProfile(ContactProfileUpdateRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.contactId() <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
        if (request.shaleClientId() <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, request.shaleClientId());
            ContactSchema schema = ContactSchema.load(con);

            StringBuilder sql = new StringBuilder("""
                    UPDATE dbo.Contacts
                    SET Name = ?,
                        FirstName = ?,
                        LastName = ?
                    """);
            if (schema.emailColumn() != null) {
                sql.append(",\n    ").append(schema.emailColumn()).append(" = ?");
            }
            if (schema.phoneColumn() != null) {
                sql.append(",\n    ").append(schema.phoneColumn()).append(" = ?");
            }
            if (schema.addressHomeColumn() != null) {
                sql.append(",\n    ").append(schema.addressHomeColumn()).append(" = ?");
            }
            if (schema.dateOfBirthColumn() != null) {
                sql.append(",\n    ").append(schema.dateOfBirthColumn()).append(" = ?");
            }
            if (schema.conditionColumn() != null) {
                sql.append(",\n    ").append(schema.conditionColumn()).append(" = ?");
            }
            if (schema.deceasedColumn() != null) {
                sql.append(",\n    ").append(schema.deceasedColumn()).append(" = ?");
            }
            if (schema.clientColumn() != null) {
                sql.append(",\n    ").append(schema.clientColumn()).append(" = ?");
            }
            if (schema.updatedAtColumn() != null) {
                sql.append(",\n    ").append(schema.updatedAtColumn()).append(" = ?");
            }
            sql.append("\nWHERE Id = ?\n  AND ShaleClientId = ?");
            sql.append(activeFilter(schema.deletedColumn(), null));
            sql.append(';');

            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                int idx = 1;
                setNullableString(ps, idx++, request.name());
                setNullableString(ps, idx++, request.firstName());
                setNullableString(ps, idx++, request.lastName());
                if (schema.emailColumn() != null) {
                    setNullableString(ps, idx++, request.email());
                }
                if (schema.phoneColumn() != null) {
                    setNullableString(ps, idx++, request.phone());
                }
                if (schema.addressHomeColumn() != null) {
                    setNullableString(ps, idx++, request.addressHome());
                }
                if (schema.dateOfBirthColumn() != null) {
                    setNullableDate(ps, idx++, request.dateOfBirth());
                }
                if (schema.conditionColumn() != null) {
                    setNullableString(ps, idx++, request.condition());
                }
                if (schema.deceasedColumn() != null) {
                    ps.setBoolean(idx++, request.deceased());
                }
                if (schema.clientColumn() != null) {
                    ps.setBoolean(idx++, request.client());
                }
                if (schema.updatedAtColumn() != null) {
                    ps.setTimestamp(idx++, Timestamp.from(Instant.now()));
                }
                ps.setInt(idx++, request.contactId());
                ps.setInt(idx++, request.shaleClientId());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update contact basic profile (id=" + request.contactId() + ")", e);
        }
    }

    private ContactDetailRow findById(Connection con, int contactId, int shaleClientId) throws SQLException {
        ContactSchema schema = ContactSchema.load(con);

        String sql = """
                SELECT
                  c.Id,
                  c.ShaleClientId,
                  COALESCE(c.Name, '') AS Name,
                  COALESCE(c.FirstName, '') AS FirstName,
                  COALESCE(c.LastName, '') AS LastName,
                  %s AS DisplayName,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s
                FROM dbo.Contacts c
                WHERE c.Id = ?
                  AND c.ShaleClientId = ?
                %s;
                """.formatted(
                displayNameExpression(schema, "c"),
                optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                optionalColumnExpression(schema.addressHomeColumn(), "c", "AddressHome"),
                optionalDateColumnExpression(schema.dateOfBirthColumn(), "c", "DateOfBirth"),
                optionalColumnExpression(schema.conditionColumn(), "c", "Condition"),
                optionalBooleanExpression(schema.deceasedColumn(), "c", "IsDeceased"),
                optionalBooleanExpression(schema.clientColumn(), "c", "IsClient"),
                updatedAtExpression(schema.updatedAtColumn(), "c"),
                activeFilter(schema.deletedColumn(), "c"));

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, contactId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Date dob = rs.getDate("DateOfBirth");
                Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                return new ContactDetailRow(
                        rs.getInt("Id"),
                        rs.getInt("ShaleClientId"),
                        rs.getString("Name"),
                        rs.getString("FirstName"),
                        rs.getString("LastName"),
                        rs.getString("DisplayName"),
                        rs.getString("Email"),
                        rs.getString("Phone"),
                        rs.getString("AddressHome"),
                        dob == null ? null : dob.toLocalDate(),
                        rs.getString("Condition"),
                        rs.getBoolean("IsDeceased"),
                        rs.getBoolean("IsClient"),
                        false,
                        updatedAt == null ? null : updatedAt.toInstant());
            }
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

    private static String displayNameExpression(ContactSchema schema, String alias) {
        return "LTRIM(RTRIM(CASE WHEN (NULLIF(LTRIM(RTRIM(COALESCE(" + alias + ".FirstName, ''))), '') IS NOT NULL) "
                + "OR (NULLIF(LTRIM(RTRIM(COALESCE(" + alias + ".LastName, ''))), '') IS NOT NULL) THEN "
                + "COALESCE(" + alias + ".FirstName, '') + CASE WHEN COALESCE(" + alias + ".FirstName, '') = '' OR COALESCE(" + alias + ".LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(" + alias + ".LastName, '') "
                + "ELSE COALESCE(" + alias + ".Name, '') END))";
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

    private static String optionalDateColumnExpression(String column, String alias, String resultAlias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS DATE) AS " + resultAlias;
        }
        return alias + "." + column + " AS " + resultAlias;
    }

    private static String optionalBooleanExpression(String column, String alias, String resultAlias) {
        if (column == null || column.isBlank()) {
            return "CAST(0 AS BIT) AS " + resultAlias;
        }
        return "COALESCE(" + alias + "." + column + ", 0) AS " + resultAlias;
    }

    private static String updatedAtExpression(String column, String alias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS DATETIME2) AS UpdatedAt";
        }
        return alias + "." + column + " AS UpdatedAt";
    }

    private static String activeFilter(String deletedColumn, String alias) {
        if (deletedColumn == null || deletedColumn.isBlank()) {
            return "";
        }
        String prefix = alias == null || alias.isBlank() ? deletedColumn : alias + "." + deletedColumn;
        return "\n  AND (COALESCE(" + prefix + ", 0) = 0 OR " + prefix + " IS NULL)";
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setString(index, null);
        } else {
            ps.setString(index, value.trim());
        }
    }

    private static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setDate(index, null);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    private record ContactSchema(
            String emailColumn,
            String phoneColumn,
            String addressHomeColumn,
            String dateOfBirthColumn,
            String conditionColumn,
            String deceasedColumn,
            String clientColumn,
            String deletedColumn,
            String updatedAtColumn
    ) {
        private static ContactSchema load(Connection con) throws SQLException {
            return new ContactSchema(
                    existingColumn(con, "Contacts", List.of("Email", "EmailPersonal", "email_personal", "email")),
                    existingColumn(con, "Contacts", List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number")),
                    existingColumn(con, "Contacts", List.of("AddressHome", "address_home", "Address", "HomeAddress")),
                    existingColumn(con, "Contacts", List.of("DateOfBirth", "date_of_birth")),
                    existingColumn(con, "Contacts", List.of("Condition", "condition")),
                    existingColumn(con, "Contacts", List.of("IsDeceased", "is_deceased")),
                    existingColumn(con, "Contacts", List.of("IsClient", "is_client")),
                    existingColumn(con, "Contacts", List.of("IsDeleted", "is_deleted")),
                    existingColumn(con, "Contacts", List.of("UpdatedAt", "updated_at")));
        }
    }

    public static String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "—";
        }
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(timestamp);
    }
}
