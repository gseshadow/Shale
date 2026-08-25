package com.shale.data.dao;

import com.shale.core.semantics.RoleSemantics;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.util.PerformanceLogging;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContactDao {

    private static final Logger LOG = LoggerFactory.getLogger(ContactDao.class);

    public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
    }

    public record DirectoryContactRow(
            int id,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phone
    ) {
    }

    public record ContactCardSummaryRow(
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
            Integer actorUserId,
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

    public record DefinitionRow(int id, String systemKey, String name, String description,
            int sortOrder, boolean active, boolean deleted) {
    }

    public record CredentialDefinitionRow(int id, String systemKey, String name, String abbreviation,
            String description, int sortOrder, boolean active, boolean deleted) {
    }

    public record AssignedDefinitionRow(long assignmentId, DefinitionRow definition, boolean historical) {
    }

    public record AssignedCredentialRow(long assignmentId, CredentialDefinitionRow definition,
            int displayOrder, boolean historical) {
    }

    public record ClassificationProfileRow(int contactId, int shaleClientId, String prefix,
            String firstName, String middleName, String lastName, String preferredName, String suffix,
            String legacyDisplayName, List<AssignedDefinitionRow> contactTypes,
            List<AssignedDefinitionRow> specialties, List<AssignedCredentialRow> credentials) {
    }


    public record RelatedCaseRow(
            long id,
            String name,
            LocalDate intakeDate,
            LocalDate statuteOfLimitationsDate,
            LocalDate tortClaimsNoticeDeadline,
            String responsibleAttorneyName,
            String responsibleAttorneyColor,
            Boolean nonEngagementLetterSent,
            String primaryStatusName,
            String primaryStatusColor,
            String practiceAreaColor,
            String partyRoleName,
            String side,
            boolean primary,
            String notes
    ) {
    }

    public record CreateContactRequest(
            int shaleClientId,
            Integer actorUserId,
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
    private final PhiAuditService phiAuditService;
    private final ContactMutationDao mutationDao;

    public ContactDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
        this.phiAuditService = new PhiAuditService(new AuditLogDao(this.db));
        this.mutationDao = new ContactMutationDao(this.db);
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
                      %s,
                      %s,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                    optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
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
                                rs.getString("FirstName"),
                                rs.getString("LastName"),
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

    public List<DirectoryContactRow> searchContacts(int shaleClientId, String query) {
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
                      %s,
                      %s,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                    optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
                    displayNameExpression(schema, "c"),
                    optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                    optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                    schema.tenantColumn(),
                    displayNameExpression(schema, "c"),
                    activeFilter(schema.deletedColumn(), "c"),
                    globalSearchClause(schema, "c"));

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                bindGlobalSearchQuery(ps, 1, shaleClientId, schema, query);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DirectoryContactRow> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(new DirectoryContactRow(
                                rs.getInt("Id"),
                                rs.getString("FirstName"),
                                rs.getString("LastName"),
                                rs.getString("DisplayName"),
                                rs.getString("Email"),
                                rs.getString("Phone")));
                    }
                    return out;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search contacts for tenant (clientId=" + shaleClientId + ")", e);
        }
    }

    public DirectoryContactRow findDirectoryContactById(int contactId, int shaleClientId) {
        long started = System.nanoTime();
        if (contactId <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
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
                      %s,
                      %s,
                      %s AS DisplayName,
                      %s,
                      %s
                    FROM dbo.Contacts c
                    WHERE c.Id = ?
                      AND c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s;
                    """.formatted(
                    optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                    optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
                    displayNameExpression(schema, "c"),
                    optionalColumnExpression(schema.emailColumn(), "c", "Email"),
                    optionalColumnExpression(schema.phoneColumn(), "c", "Phone"),
                    schema.tenantColumn(),
                    displayNameExpression(schema, "c"),
                    activeFilter(schema.deletedColumn(), "c"));

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, contactId);
                ps.setInt(2, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        logPerf("contacts.directory.detail.query", "contactId=" + contactId + " tenantId=" + shaleClientId + " found=false", started);
                        return null;
                    }
                    DirectoryContactRow row = new DirectoryContactRow(
                            rs.getInt("Id"),
                            rs.getString("FirstName"),
                            rs.getString("LastName"),
                            rs.getString("DisplayName"),
                            rs.getString("Email"),
                            rs.getString("Phone"));
                    logPerf("contacts.directory.detail.query", "contactId=" + contactId + " tenantId=" + shaleClientId + " found=true", started);
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load directory contact by id (id=" + contactId + ")", e);
        }
    }

    public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage(int shaleClientId, int page, int pageSize, String searchQuery) {
        long started = System.nanoTime();
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

            long countStarted = System.nanoTime();
            long total = countDirectoryContacts(con, schema, shaleClientId, searchQuery);
            logPerf("contacts.directory.count", "tenantId=" + shaleClientId + " page=" + page + " queryLength=" + normalizedQueryLength(searchQuery) + " total=" + total, countStarted);
            if (total == 0) {
                logPerf("contacts.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedQueryLength(searchQuery) + " rows=0 total=0 fullDetailHydration=false selectedFields=id,displayName,email,phone", started);
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }

            String searchClause = lightweightSearchClause(schema, "c");
            String displayName = lightweightDisplayNameExpression(schema, "c");
            String sql = """
                    SELECT
                      c.Id,
                      %s AS DisplayName,
                      %s AS Email,
                      %s AS Phone
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND %s IS NOT NULL
                    %s
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
                    """.formatted(
                    displayName,
                    lightweightColumnExpression(schema.emailColumn(), "c"),
                    lightweightColumnExpression(schema.phoneColumn(), "c"),
                    schema.tenantColumn(),
                    displayName,
                    activeFilter(schema.deletedColumn(), "c"),
                    searchClause);

            List<ContactCardSummaryRow> out = new ArrayList<>(pageSize);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int idx = 1;
                idx = bindDirectoryQuery(ps, idx, shaleClientId, schema, searchQuery);
                ps.setInt(idx++, page * pageSize);
                ps.setInt(idx, pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ContactCardSummaryRow(
                                rs.getInt("Id"),
                                rs.getString("DisplayName"),
                                rs.getString("Email"),
                                rs.getString("Phone")));
                    }
                }
            }
            PagedResult<ContactCardSummaryRow> result = new PagedResult<>(List.copyOf(out), page, pageSize, total);
            logPerf("contacts.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedQueryLength(searchQuery) + " rows=" + out.size() + " total=" + total + " fullDetailHydration=false selectedFields=id,displayName,email,phone", started);
            return result;
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
        long started = System.nanoTime();
        if (contactId <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            ContactDetailRow row = findById(con, contactId, shaleClientId);
            logPerf("contacts.detail.query", "contactId=" + contactId + " tenantId=" + shaleClientId + " found=" + (row != null), started);
            return row;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
        }
    }

    /**
     * Loads the selectable overlay for ContactTypes or Specialties. A deleted tenant override is a
     * reset and permits global fallback; an inactive tenant override still masks its global row.
     */
    public List<DefinitionRow> listEffectiveDefinitions(String table, int shaleClientId) {
        if (!"ContactTypes".equals(table) && !"Specialties".equals(table)) {
            throw new IllegalArgumentException("Unsupported contact definition table");
        }
        validateTenantId(shaleClientId);
        String sql = """
                WITH visible AS (
                  SELECT d.Id,d.SystemKey,d.Name,d.Description,d.SortOrder,d.IsActive,d.IsDeleted,
                    ROW_NUMBER() OVER (PARTITION BY d.SystemKey
                      ORDER BY CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END,d.Id) rn
                  FROM dbo.%s d
                  WHERE (d.ShaleClientId=? OR d.ShaleClientId IS NULL) AND d.IsDeleted=0
                )
                SELECT Id,SystemKey,Name,Description,SortOrder,IsActive,IsDeleted
                FROM visible WHERE rn=1 AND IsActive=1
                ORDER BY SortOrder,Name,Id;
                """.formatted(table);
        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, shaleClientId);
                ps.setInt(2, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DefinitionRow> rows = new ArrayList<>();
                    while (rs.next()) rows.add(mapDefinition(rs));
                    return rows;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load effective " + table + " (clientId=" + shaleClientId + ")", e);
        }
    }

    public List<CredentialDefinitionRow> listEffectiveCredentialDefinitions(int shaleClientId) {
        validateTenantId(shaleClientId);
        String sql = """
                WITH visible AS (
                  SELECT d.Id,d.SystemKey,d.Name,d.Abbreviation,d.Description,d.SortOrder,d.IsActive,d.IsDeleted,
                    ROW_NUMBER() OVER (PARTITION BY d.SystemKey
                      ORDER BY CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END,d.Id) rn
                  FROM dbo.CredentialDefinitions d
                  WHERE (d.ShaleClientId=? OR d.ShaleClientId IS NULL) AND d.IsDeleted=0
                )
                SELECT Id,SystemKey,Name,Abbreviation,Description,SortOrder,IsActive,IsDeleted
                FROM visible WHERE rn=1 AND IsActive=1
                ORDER BY SortOrder,Name,Id;
                """;
        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, shaleClientId);
                ps.setInt(2, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<CredentialDefinitionRow> rows = new ArrayList<>();
                    while (rs.next()) rows.add(mapCredentialDefinition(rs));
                    return rows;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load effective CredentialDefinitions (clientId=" + shaleClientId + ")", e);
        }
    }

    public ClassificationProfileRow findClassificationProfile(int contactId, int shaleClientId) {
        if (contactId <= 0) throw new IllegalArgumentException("contactId must be > 0");
        validateTenantId(shaleClientId);
        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            String contactSql = """
                    SELECT c.Id,c.Prefix,c.FirstName,c.MiddleName,c.LastName,c.PreferredName,c.Suffix,
                           COALESCE(NULLIF(LTRIM(RTRIM(c.Name)),''),NULLIF(LTRIM(RTRIM(c.WorkName)),''),
                             NULLIF(LTRIM(RTRIM(CONCAT(c.FirstName,' ',c.LastName))),'')) LegacyDisplayName
                    FROM dbo.Contacts c WHERE c.Id=? AND c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0;
                    """;
            try (PreparedStatement ps = con.prepareStatement(contactSql)) {
                ps.setInt(1, contactId); ps.setInt(2, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    String prefix=rs.getString("Prefix"), first=rs.getString("FirstName");
                    String middle=rs.getString("MiddleName"), last=rs.getString("LastName");
                    String preferred=rs.getString("PreferredName"), suffix=rs.getString("Suffix");
                    String display=rs.getString("LegacyDisplayName");
                    List<AssignedDefinitionRow> types = loadAssignedDefinitions(con, "ContactContactTypes", "ContactTypeId", "ContactTypes", contactId, shaleClientId);
                    List<AssignedDefinitionRow> specialties = loadAssignedDefinitions(con, "ContactSpecialties", "SpecialtyId", "Specialties", contactId, shaleClientId);
                    List<AssignedCredentialRow> credentials = loadAssignedCredentials(con, contactId, shaleClientId);
                    return new ClassificationProfileRow(contactId, shaleClientId, prefix, first, middle, last,
                            preferred, suffix, display, types, specialties, credentials);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load Contact classification profile (id=" + contactId + ")", e);
        }
    }

    public boolean updateBasicProfile(ContactProfileUpdateRequest request) {
        long started = System.nanoTime();
        Objects.requireNonNull(request, "request");
        if (request.contactId() <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
        if (request.shaleClientId() <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, request.shaleClientId());
            ContactDetailRow before = findById(request.contactId(), request.shaleClientId());
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
                boolean updated = ps.executeUpdate() > 0;
                if (updated) {
                    ContactDetailRow after = findById(request.contactId(), request.shaleClientId());
                    if (before != null && after != null) {
                        phiAuditService.auditUpdate(
                                request.actorUserId(),
                                "Contacts",
                                "Condition",
                                (long) request.contactId(),
                                before.condition(),
                                after.condition());
                    }
                }
                logPerf("contacts.save.query", "contactId=" + request.contactId() + " tenantId=" + request.shaleClientId() + " updated=" + updated, started);
                return updated;
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
        String normalizedName = normalizeOptional(request.name());
        String normalizedFirstName = normalizeOptional(request.firstName());
        String normalizedLastName = normalizeOptional(request.lastName());
        if (normalizedName == null && normalizedFirstName == null && normalizedLastName == null) {
            throw new IllegalArgumentException("At least a display name, first name, or last name is required.");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, request.shaleClientId());
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);

            List<String> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            if (schema.nameColumn() != null) {
                columns.add(schema.nameColumn());
                values.add(normalizedName == null ? buildDisplayName(normalizedFirstName, normalizedLastName) : normalizedName);
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
            if (schema.addressHomeColumn() != null) {
                columns.add(schema.addressHomeColumn());
                values.add(normalizeOptional(request.addressHome()));
            }
            if (schema.dateOfBirthColumn() != null) {
                columns.add(schema.dateOfBirthColumn());
                values.add(request.dateOfBirth());
            }
            if (schema.conditionColumn() != null) {
                columns.add(schema.conditionColumn());
                values.add(normalizeOptional(request.condition()));
            }
            if (schema.deceasedColumn() != null) {
                columns.add(schema.deceasedColumn());
                values.add(request.deceased());
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
                    int contactId = rs.getInt(1);
                    if (normalizeOptional(request.condition()) != null) {
                        phiAuditService.auditUpdate(
                                request.actorUserId(),
                                "Contacts",
                                "Condition",
                                (long) contactId,
                                null,
                                normalizeOptional(request.condition()));
                    }
                    return contactId;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create contact", e);
        }
    }

    public boolean softDeleteContact(int contactId, int shaleClientId) {
        if (contactId <= 0) {
            throw new IllegalArgumentException("contactId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);
            if (schema.deletedColumn() == null || schema.deletedColumn().isBlank()) {
                throw new IllegalStateException("Contacts table does not support soft delete.");
            }

            String cleanupCasePartiesSql = """
                    DELETE cp
                    FROM dbo.CaseParties cp
                    WHERE cp.ContactId = ?
                      AND EXISTS (
                          SELECT 1
                          FROM dbo.Cases c
                          WHERE c.Id = cp.CaseId
                            AND c.ShaleClientId = ?
                      );
                    """;

            StringBuilder sql = new StringBuilder("""
                    UPDATE dbo.Contacts
                    SET
                      """);
            sql.append(schema.deletedColumn()).append(" = 1");
            if (schema.updatedAtColumn() != null && !schema.updatedAtColumn().isBlank()) {
                sql.append(",\n  ").append(schema.updatedAtColumn()).append(" = SYSUTCDATETIME()");
            }
            sql.append("\nWHERE Id = ?\n  AND ").append(schema.tenantColumn()).append(" = ?");
            sql.append(activeFilter(schema.deletedColumn(), null));
            sql.append(';');

            boolean previousAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                try (PreparedStatement cleanupPs = con.prepareStatement(cleanupCasePartiesSql)) {
                    cleanupPs.setInt(1, contactId);
                    cleanupPs.setInt(2, shaleClientId);
                    cleanupPs.executeUpdate();
                }

                boolean deleted;
                try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                    ps.setInt(1, contactId);
                    ps.setInt(2, shaleClientId);
                    deleted = ps.executeUpdate() > 0;
                }

                con.commit();
                return deleted;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to soft delete contact (id=" + contactId + ")", e);
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

    private static void logPerf(String area, String fields, long startedNanos) {
        long elapsedMs = PerformanceLogging.elapsedMs(startedNanos);
        if (PerformanceLogging.isSlow(elapsedMs)) {
            LOG.warn("PERF {} {} elapsedMs={} slow=true thresholdMs={}", area, fields, elapsedMs, PerformanceLogging.slowThresholdMs());
        } else if (PerformanceLogging.isEnabled()) {
            LOG.debug("PERF {} {} elapsedMs={}", area, fields, elapsedMs);
        }
    }

    private static int normalizedQueryLength(String query) {
        return query == null ? 0 : query.trim().length();
    }

    private static void verifyTenantMatchesSession(Connection con, int shaleClientId) throws SQLException {
        int sessionClientId = requireCurrentShaleClientId(con);
        if (sessionClientId != shaleClientId) {
            throw new IllegalStateException("Tenant mismatch. Session clientId=" + sessionClientId + ", requested=" + shaleClientId);
        }
    }

    private static void validateTenantId(int shaleClientId) {
        if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
    }

    private static DefinitionRow mapDefinition(ResultSet rs) throws SQLException {
        return new DefinitionRow(rs.getInt("Id"), rs.getString("SystemKey"), rs.getString("Name"),
                rs.getString("Description"), rs.getInt("SortOrder"), rs.getBoolean("IsActive"),
                rs.getBoolean("IsDeleted"));
    }

    private static CredentialDefinitionRow mapCredentialDefinition(ResultSet rs) throws SQLException {
        return new CredentialDefinitionRow(rs.getInt("Id"), rs.getString("SystemKey"), rs.getString("Name"),
                rs.getString("Abbreviation"), rs.getString("Description"), rs.getInt("SortOrder"),
                rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"));
    }

    private static List<AssignedDefinitionRow> loadAssignedDefinitions(Connection con, String assignmentTable,
            String definitionIdColumn, String definitionTable, int contactId, int shaleClientId) throws SQLException {
        String sql = """
                SELECT a.Id AssignmentId,d.Id,d.SystemKey,d.Name,d.Description,d.SortOrder,d.IsActive,d.IsDeleted
                FROM dbo.%s a JOIN dbo.%s d ON d.Id=a.%s
                  AND (d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL)
                WHERE a.ContactId=? AND a.ShaleClientId=? AND a.IsDeleted=0
                ORDER BY d.SortOrder,d.Name,d.Id;
                """.formatted(assignmentTable, definitionTable, definitionIdColumn);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, contactId); ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AssignedDefinitionRow> rows = new ArrayList<>();
                while (rs.next()) {
                    DefinitionRow definition = mapDefinition(rs);
                    rows.add(new AssignedDefinitionRow(rs.getLong("AssignmentId"), definition,
                            !definition.active() || definition.deleted()));
                }
                return rows;
            }
        }
    }

    private static List<AssignedCredentialRow> loadAssignedCredentials(Connection con, int contactId,
            int shaleClientId) throws SQLException {
        String sql = """
                SELECT a.Id AssignmentId,a.DisplayOrder,d.Id,d.SystemKey,d.Name,d.Abbreviation,
                       d.Description,d.SortOrder,d.IsActive,d.IsDeleted
                FROM dbo.ContactCredentials a JOIN dbo.CredentialDefinitions d
                  ON d.Id=a.CredentialDefinitionId
                 AND (d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL)
                WHERE a.ContactId=? AND a.ShaleClientId=? AND a.IsDeleted=0
                ORDER BY a.DisplayOrder,d.SortOrder,d.Name,d.Id;
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, contactId); ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AssignedCredentialRow> rows = new ArrayList<>();
                while (rs.next()) {
                    CredentialDefinitionRow definition = mapCredentialDefinition(rs);
                    rows.add(new AssignedCredentialRow(rs.getLong("AssignmentId"), definition,
                            rs.getInt("DisplayOrder"), !definition.active() || definition.deleted()));
                }
                return rows;
            }
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

    private static String lightweightColumnExpression(String column, String alias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS NVARCHAR(255))";
        }
        return "NULLIF(LTRIM(RTRIM(CONVERT(NVARCHAR(255), " + alias + "." + column + "))), '')";
    }

    private static String lightweightDisplayNameExpression(ContactSchema schema, String alias) {
        String first = lightweightColumnExpression(schema.firstNameColumn(), alias);
        String last = lightweightColumnExpression(schema.lastNameColumn(), alias);
        String name = lightweightColumnExpression(schema.nameColumn(), alias);
        return "NULLIF(LTRIM(RTRIM(CASE WHEN (" + first + " IS NOT NULL) OR (" + last + " IS NOT NULL) THEN "
                + "COALESCE(" + first + ", '') + CASE WHEN COALESCE(" + first + ", '') = '' OR COALESCE(" + last + ", '') = '' THEN '' ELSE ' ' END + COALESCE(" + last + ", '') "
                + "ELSE COALESCE(" + name + ", '') END)), '')";
    }

    private static String phoneDigitsExpression(String column, String alias) {
        if (column == null || column.isBlank()) {
            return "CAST(NULL AS NVARCHAR(255))";
        }
        String value = "COALESCE(" + alias + "." + column + ", '')";
        return "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" + value
                + ", ' ', ''), '-', ''), '(', ''), ')', ''), '.', ''), '+', ''), '/', '')";
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
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                  )
                """.formatted(
                coreTextExpression(schema.nameColumn(), alias),
                coreTextExpression(schema.firstNameColumn(), alias),
                coreTextExpression(schema.lastNameColumn(), alias),
                displayNameExpression(schema, alias),
                coreTextExpression(schema.emailColumn(), alias),
                coreTextExpression(schema.phoneColumn(), alias));
    }

    private static String lightweightSearchClause(ContactSchema schema, String alias) {
        return """
                  AND (
                    ? = ''
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                  )
                """.formatted(
                lightweightColumnExpression(schema.nameColumn(), alias),
                lightweightColumnExpression(schema.firstNameColumn(), alias),
                lightweightColumnExpression(schema.lastNameColumn(), alias),
                lightweightDisplayNameExpression(schema, alias),
                lightweightColumnExpression(schema.emailColumn(), alias),
                lightweightColumnExpression(schema.phoneColumn(), alias));
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
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        return idx;
    }


    private static String globalSearchClause(ContactSchema schema, String alias) {
        return """
                  AND (
                    ? = ''
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR LOWER(%s) LIKE ?
                    OR (? <> '' AND %s LIKE ?)
                  )
                """.formatted(
                coreTextExpression(schema.firstNameColumn(), alias),
                coreTextExpression(schema.lastNameColumn(), alias),
                displayNameExpression(schema, alias),
                coreTextExpression(schema.emailColumn(), alias),
                phoneDigitsExpression(schema.phoneColumn(), alias));
    }

    private static int bindGlobalSearchQuery(PreparedStatement ps,
                                             int idx,
                                             int shaleClientId,
                                             ContactSchema schema,
                                             String searchQuery) throws SQLException {
        ps.setInt(idx++, shaleClientId);
        String normalizedSearch = normalizeSearchQuery(searchQuery);
        String likeValue = likeParameter(normalizedSearch);
        String phoneDigits = normalizePhoneDigits(searchQuery);
        String phoneLikeValue = likeParameter(phoneDigits);
        ps.setString(idx++, normalizedSearch);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, likeValue);
        ps.setString(idx++, phoneDigits);
        ps.setString(idx++, phoneLikeValue);
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

    private static String normalizePhoneDigits(String value) {
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

    private static LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
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
        // intentionally no-op: temporary schema/debug tracing removed in stabilization pass
    }

    private static void logFindByIdAttempt(int contactId, int shaleClientId) {
        // intentionally no-op: temporary lookup/debug tracing removed in stabilization pass
    }

    private static void logFindByIdResult(int contactId, int shaleClientId, boolean found) {
        // intentionally no-op: temporary lookup/debug tracing removed in stabilization pass
    }

    public static String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "—";
        }
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(timestamp);
    }

    public com.shale.core.service.ContactServicePort.DefinitionMutationResult createDefinition(com.shale.core.service.ContactServicePort.CreateDefinitionCommand c){return mutationDao.create(c);}
    public com.shale.core.service.ContactServicePort.DefinitionMutationResult updateDefinition(com.shale.core.service.ContactServicePort.UpdateDefinitionCommand c){return mutationDao.update(c);}
    public com.shale.core.service.ContactServicePort.DefinitionMutationResult setDefinitionActive(com.shale.core.service.ContactServicePort.DefinitionLifecycleCommand c){return mutationDao.lifecycle(c,"active");}
    public com.shale.core.service.ContactServicePort.DefinitionMutationResult removeDefinition(com.shale.core.service.ContactServicePort.DefinitionLifecycleCommand c){return mutationDao.lifecycle(c,"remove");}
    public com.shale.core.service.ContactServicePort.DefinitionMutationResult restoreDefinition(com.shale.core.service.ContactServicePort.DefinitionLifecycleCommand c){return mutationDao.lifecycle(c,"restore");}
    public com.shale.core.service.ContactServicePort.AssignmentMutationResult assignClassification(com.shale.core.service.ContactServicePort.AssignClassificationCommand c){return mutationDao.assign(c);}
    public com.shale.core.service.ContactServicePort.AssignmentMutationResult removeClassification(com.shale.core.service.ContactServicePort.AssignmentLifecycleCommand c){return mutationDao.lifecycle(c,false);}
    public com.shale.core.service.ContactServicePort.AssignmentMutationResult restoreClassification(com.shale.core.service.ContactServicePort.AssignmentLifecycleCommand c){return mutationDao.lifecycle(c,true);}
    public java.util.List<com.shale.core.service.ContactServicePort.AssignmentMutationResult> reorderCredentials(com.shale.core.service.ContactServicePort.ReorderCredentialsCommand c){return mutationDao.reorder(c);}

}
