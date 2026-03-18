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
import java.util.Locale;
import java.util.Objects;

public final class ContactDao {

    public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
    }

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

    public record CreateContactRequest(
            int shaleClientId,
            String firstName,
            String lastName,
            String email,
            String phone,
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
            logDetectedCoreColumns(schema);
            String sql = """
                    SELECT
                      c.Id,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    displayNameExpression(schema, "c"),
                    optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                    optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                    schema.tenantColumn(),
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

    public PagedResult<DirectoryContactRow> findDirectoryContactsPage(int shaleClientId, int page, int pageSize, String searchQuery) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);

            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);

            long total = countDirectoryContacts(con, schema, shaleClientId, searchQuery);
            if (total == 0) {
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }

            String searchClause = searchClause(schema, "c");
            String sql = """
                    SELECT
                      c.Id,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
                    """.formatted(
                    displayNameExpression(schema, "c"),
                    optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                    optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                    schema.tenantColumn(),
                    displayNameExpression(schema, "c"),
                    activeFilter(schema.deletedColumn(), "c"),
                    searchClause);

            List<DirectoryContactRow> out = new ArrayList<>(pageSize);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int idx = 1;
                idx = bindDirectoryQuery(ps, idx, shaleClientId, schema, searchQuery);
                ps.setInt(idx++, page * pageSize);
                ps.setInt(idx, pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new DirectoryContactRow(
                                rs.getInt("Id"),
                                rs.getString("DisplayName"),
                                rs.getString("Email"),
                                rs.getString("Phone")));
                    }
                }
            }
            return new PagedResult<>(List.copyOf(out), page, pageSize, total);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contacts page for tenant (clientId=" + shaleClientId + ", page=" + page + ")", e);
        }
    }

    public long countDirectoryContacts(int shaleClientId, String searchQuery) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);
            return countDirectoryContacts(con, schema, shaleClientId, searchQuery);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count contacts for tenant (clientId=" + shaleClientId + ")", e);
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
            logDetectedCoreColumns(schema);

            StringBuilder sql = new StringBuilder("""
                    UPDATE dbo.Contacts
                    SET
                    """);
            boolean hasAssignments = false;
            hasAssignments = appendAssignment(sql, hasAssignments, schema.nameColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.firstNameColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.lastNameColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.emailColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.phoneColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.addressHomeColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.dateOfBirthColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.conditionColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.deceasedColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.clientColumn());
            hasAssignments = appendAssignment(sql, hasAssignments, schema.updatedAtColumn());
            if (!hasAssignments) {
                throw new SQLException("No updatable contact columns were detected.");
            }
            sql.append("\nWHERE Id = ?\n  AND ").append(schema.tenantColumn()).append(" = ?");
            sql.append(activeFilter(schema.deletedColumn(), null));
            sql.append(';');

            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                int idx = 1;
                if (schema.nameColumn() != null) {
                    setNullableString(ps, idx++, request.name());
                }
                if (schema.firstNameColumn() != null) {
                    setNullableString(ps, idx++, request.firstName());
                }
                if (schema.lastNameColumn() != null) {
                    setNullableString(ps, idx++, request.lastName());
                }
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

    public int createContact(CreateContactRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.shaleClientId() <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        String normalizedFirstName = normalizeOptional(request.firstName());
        String normalizedLastName = normalizeOptional(request.lastName());
        if (normalizedFirstName == null && normalizedLastName == null) {
            throw new IllegalArgumentException("At least a first or last name is required.");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, request.shaleClientId());
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);

            List<String> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            if (schema.nameColumn() != null) {
                columns.add(schema.nameColumn());
                values.add(buildDisplayName(normalizedFirstName, normalizedLastName));
            }
            if (schema.firstNameColumn() != null) {
                columns.add(schema.firstNameColumn());
                values.add(normalizedFirstName);
            }
            if (schema.lastNameColumn() != null) {
                columns.add(schema.lastNameColumn());
                values.add(normalizedLastName);
            }
            if (schema.emailColumn() != null) {
                columns.add(schema.emailColumn());
                values.add(normalizeOptional(request.email()));
            }
            if (schema.phoneColumn() != null) {
                columns.add(schema.phoneColumn());
                values.add(normalizeOptional(request.phone()));
            }
            if (schema.clientColumn() != null) {
                columns.add(schema.clientColumn());
                values.add(request.client());
            }
            if (schema.deletedColumn() != null) {
                columns.add(schema.deletedColumn());
                values.add(false);
            }
            Timestamp now = Timestamp.from(Instant.now());
            if (schema.createdAtColumn() != null) {
                columns.add(schema.createdAtColumn());
                values.add(now);
            }
            if (schema.updatedAtColumn() != null) {
                columns.add(schema.updatedAtColumn());
                values.add(now);
            }
            columns.add(schema.tenantColumn());
            values.add(request.shaleClientId());

            String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
            String joinedColumns = String.join(",\n  ", columns);
            String sql = """
                    INSERT INTO dbo.Contacts (
                      %s
                    )
                    OUTPUT INSERTED.Id
                    VALUES (%s);
                    """.formatted(joinedColumns, placeholders);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int idx = 1;
                for (Object value : values) {
                    setStatementValue(ps, idx++, value);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Failed to create contact.");
                    }
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create contact", e);
        }
    }

    private ContactDetailRow findById(Connection con, int contactId, int shaleClientId) throws SQLException {
        ContactSchema schema = ContactSchema.load(con);
        logDetectedCoreColumns(schema);
        logFindByIdAttempt(contactId, shaleClientId);

        String sql = """
                SELECT
                  c.Id,
                  %s,
                  %s,
                  %s,
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
                  AND c.%s = ?
                %s;
                """.formatted(
                optionalColumnExpression(schema.nameColumn(), "c", "Name"),
                optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
                displayNameExpression(schema, "c"),
                optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                optionalColumnExpression(schema.addressHomeColumn(), "c", "AddressHome"),
                optionalDateColumnExpression(schema.dateOfBirthColumn(), "c", "DateOfBirth"),
                optionalColumnExpression(schema.conditionColumn(), "c", "Condition"),
                optionalBooleanExpression(schema.deceasedColumn(), "c", "IsDeceased"),
                optionalBooleanExpression(schema.clientColumn(), "c", "IsClient"),
                updatedAtExpression(schema.updatedAtColumn(), "c"),
                schema.tenantColumn(),
                activeFilter(schema.deletedColumn(), "c"));

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, contactId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    logFindByIdResult(contactId, shaleClientId, false);
                    return null;
                }
                logFindByIdResult(contactId, shaleClientId, true);
                Date dob = rs.getDate("DateOfBirth");
                Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                return new ContactDetailRow(
                        rs.getInt("Id"),
                        shaleClientId,
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
        String sql = "SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT);";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("ShaleClientId session context is missing.");
            }
            int clientId = rs.getInt(1);
            if (rs.wasNull() || clientId <= 0) {
                throw new IllegalStateException("ShaleClientId session context is missing.");
            }
            return clientId;
        }
    }

    private static String displayNameExpression(ContactSchema schema, String alias) {
        String first = coreTextExpression(schema.firstNameColumn(), alias);
        String last = coreTextExpression(schema.lastNameColumn(), alias);
        String name = coreTextExpression(schema.nameColumn(), alias);
        return "LTRIM(RTRIM(CASE WHEN (" + first + " IS NOT NULL) OR (" + last + " IS NOT NULL) THEN "
                + "COALESCE(" + first + ", '') + CASE WHEN COALESCE(" + first + ", '') = '' OR COALESCE(" + last + ", '') = '' THEN '' ELSE ' ' END + COALESCE(" + last + ", '') "
                + "ELSE COALESCE(" + name + ", '') END))";
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

    private static String coreTextExpression(String column, String alias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS NVARCHAR(255))";
        }
        return "NULLIF(LTRIM(RTRIM(" + alias + "." + column + ")), '')";
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

    private static String searchClause(ContactSchema schema, String alias) {
        return """
                  AND (
                    ? = ''
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                  )
                """.formatted(
                displayNameExpression(schema, alias),
                coreTextExpression(schema.emailColumn(), alias),
                coreTextExpression(schema.phoneColumn(), alias));
    }

    private static int bindDirectoryQuery(PreparedStatement ps,
                                          int idx,
                                          int shaleClientId,
                                          ContactSchema schema,
                                          String searchQuery) throws SQLException {
        ps.setInt(idx++, shaleClientId);
        String normalizedSearch = normalizeSearchQuery(searchQuery);
        String likeValue = likeParameter(normalizedSearch);
        ps.setString(idx++, normalizedSearch);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        return idx;
    }

    private static long countDirectoryContacts(Connection con,
                                               ContactSchema schema,
                                               int shaleClientId,
                                               String searchQuery) throws SQLException {
        String sql = """
                SELECT COUNT_BIG(*)
                FROM dbo.Contacts c
                WHERE c.%s = ?
                  AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                %s
                %s;
                """.formatted(
                schema.tenantColumn(),
                displayNameExpression(schema, "c"),
                activeFilter(schema.deletedColumn(), "c"),
                searchClause(schema, "c"));

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindDirectoryQuery(ps, 1, shaleClientId, schema, searchQuery);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getLong(1);
            }
        }
    }

    private static String normalizeSearchQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String likeParameter(String normalizedQuery) {
        return "%" + normalizedQuery + "%";
    }

    private static boolean appendAssignment(StringBuilder sql, boolean hasAssignments, String column) {
        if (column == null || column.isBlank()) {
            return hasAssignments;
        }
        if (hasAssignments) {
            sql.append(",\n");
        } else {
            sql.append('\n');
        }
        sql.append("    ").append(column).append(" = ?");
        return true;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String buildDisplayName(String firstName, String lastName) {
        String first = normalizeOptional(firstName);
        String last = normalizeOptional(lastName);
        if (first == null) {
            return last == null ? null : last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static void setStatementValue(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (value instanceof String s) {
            ps.setString(index, s);
            return;
        }
        if (value instanceof Boolean b) {
            ps.setBoolean(index, b);
            return;
        }
        if (value instanceof Timestamp ts) {
            ps.setTimestamp(index, ts);
            return;
        }
        if (value instanceof LocalDate d) {
            ps.setDate(index, Date.valueOf(d));
            return;
        }
        if (value instanceof Integer i) {
            ps.setInt(index, i);
            return;
        }
        ps.setObject(index, value);
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
            String tenantColumn,
            String nameColumn,
            String firstNameColumn,
            String lastNameColumn,
            String emailColumn,
            String phoneColumn,
            String addressHomeColumn,
            String dateOfBirthColumn,
            String conditionColumn,
            String deceasedColumn,
            String clientColumn,
            String deletedColumn,
            String createdAtColumn,
            String updatedAtColumn
    ) {
        private static ContactSchema load(Connection con) throws SQLException {
            return new ContactSchema(
                    requiredColumn(con, "Contacts", List.of("ShaleClientId", "shale_client_id", "ClientId", "client_id")),
                    existingColumn(con, "Contacts", List.of("Name", "FullName", "DisplayName", "name")),
                    existingColumn(con, "Contacts", List.of("FirstName", "NameFirst", "name_first", "first_name")),
                    existingColumn(con, "Contacts", List.of("LastName", "NameLast", "name_last", "last_name")),
                    existingColumn(con, "Contacts", List.of("Email", "EmailPersonal", "email_personal", "email")),
                    existingColumn(con, "Contacts", List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number")),
                    existingColumn(con, "Contacts", List.of("AddressHome", "address_home", "Address", "HomeAddress")),
                    existingColumn(con, "Contacts", List.of("DateOfBirth", "date_of_birth")),
                    existingColumn(con, "Contacts", List.of("Condition", "condition")),
                    existingColumn(con, "Contacts", List.of("IsDeceased", "is_deceased")),
                    existingColumn(con, "Contacts", List.of("IsClient", "is_client")),
                    existingColumn(con, "Contacts", List.of("IsDeleted", "is_deleted")),
                    existingColumn(con, "Contacts", List.of("CreatedAt", "created_at")),
                    existingColumn(con, "Contacts", List.of("UpdatedAt", "updated_at")));
        }
    }

    private static String requiredColumn(Connection con, String table, List<String> candidates) throws SQLException {
        String column = existingColumn(con, table, candidates);
        if (column == null || column.isBlank()) {
            throw new SQLException("Required column not found for " + table + ": one of " + candidates);
        }
        return column;
    }

    private static void logDetectedCoreColumns(ContactSchema schema) {
        System.out.println("[TEMP][ContactDao] detected columns: tenant=" + schema.tenantColumn()
                + ", name=" + schema.nameColumn()
                + ", firstName=" + schema.firstNameColumn()
                + ", lastName=" + schema.lastNameColumn()
                + ", email=" + schema.emailColumn()
                + ", phone=" + schema.phoneColumn());
    }

    private static void logFindByIdAttempt(int contactId, int shaleClientId) {
        System.out.println("[TEMP][ContactDao] findById contactId=" + contactId + ", shaleClientId=" + shaleClientId);
    }

    private static void logFindByIdResult(int contactId, int shaleClientId, boolean found) {
        System.out.println("[TEMP][ContactDao] findById contactId=" + contactId + ", shaleClientId=" + shaleClientId + ", returnedRow=" + found);
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
