package com.shale.data.dao;

import com.shale.core.semantics.RoleSemantics;
import com.shale.core.service.ContactServicePort.DefinitionCategory;
import com.shale.core.service.ContactServicePort.DirectoryFilters;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.util.PerformanceLogging;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
            String phone,
            List<String> credentialAbbreviations
    ) {
        public DirectoryContactRow {
            credentialAbbreviations = credentialAbbreviations == null ? List.of() : List.copyOf(credentialAbbreviations);
        }
        public DirectoryContactRow(int id, String firstName, String lastName, String displayName, String email, String phone) {
            this(id, firstName, lastName, displayName, email, phone, List.of());
        }
    }

    public record ContactCardSummaryRow(
            int id,
            String displayName,
            String email,
            String phone,
            List<String> credentialAbbreviations
    ) {
        public ContactCardSummaryRow { credentialAbbreviations = List.copyOf(credentialAbbreviations); }
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
            String address,
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
            String address,
            LocalDate dateOfBirth,
            String condition,
            boolean deceased,
            boolean client
    ) {
    }

    public record DefinitionRow(int id, String systemKey, String name, String description, String color,
            int sortOrder, boolean active, boolean deleted) {
    }

    public record CredentialDefinitionRow(int id, String systemKey, String name, String abbreviation,
            String description, String color, int sortOrder, boolean active, boolean deleted) {
    }

    public record AdministrationDefinitionRow(DefinitionCategory category, int id, Integer shaleClientId,
            String systemKey, String name, String abbreviation, String description, String color, int sortOrder,
            boolean active, boolean deleted, byte[] rowVer) {
        public AdministrationDefinitionRow { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    }

    public record AssignedDefinitionRow(long assignmentId, DefinitionRow definition, boolean historical, byte[] rowVer) {
        public AssignedDefinitionRow { rowVer=rowVer==null?null:rowVer.clone(); }
        @Override public byte[] rowVer(){return rowVer==null?null:rowVer.clone();}
    }

    public record AssignedCredentialRow(long assignmentId, CredentialDefinitionRow definition,
            int displayOrder, boolean historical, byte[] rowVer) {
        public AssignedCredentialRow { rowVer=rowVer==null?null:rowVer.clone(); }
        @Override public byte[] rowVer(){return rowVer==null?null:rowVer.clone();}
    }

    public record ClassificationProfileRow(int contactId, int shaleClientId, String prefix,
            String firstName, String middleName, String lastName, String preferredName, String suffix,
            String legacyDisplayName, java.time.LocalDate dateOfBirth, String condition, boolean deceased,
            Instant contactUpdatedAt, List<AssignedDefinitionRow> contactTypes,
            List<AssignedDefinitionRow> specialties, List<AssignedCredentialRow> credentials,
            List<ContactPhoneNumberRow> phoneNumbers, List<ContactEmailAddressRow> emailAddresses,
            List<ContactAddressRow> addresses) {
		public ClassificationProfileRow(int contactId,int shaleClientId,String prefix,String firstName,String middleName,String lastName,String preferredName,String suffix,String legacyDisplayName,Instant contactUpdatedAt,List<AssignedDefinitionRow> contactTypes,List<AssignedDefinitionRow> specialties,List<AssignedCredentialRow> credentials){this(contactId,shaleClientId,prefix,firstName,middleName,lastName,preferredName,suffix,legacyDisplayName,null,null,false,contactUpdatedAt,contactTypes,specialties,credentials,List.of(),List.of(),List.of());}
    }

    public record ContactPhoneNumberRow(long id,String kind,String displayNumber,String normalizedNumber,
            String extension,boolean primary,int sortOrder,boolean deleted,Instant createdAt,Instant updatedAt,byte[] rowVer){
        public ContactPhoneNumberRow{rowVer=rowVer==null?null:rowVer.clone();}@Override public byte[] rowVer(){return rowVer==null?null:rowVer.clone();}}
    public record ContactEmailAddressRow(long id,String kind,String emailAddress,String normalizedEmail,
            boolean primary,int sortOrder,boolean deleted,Instant createdAt,Instant updatedAt,byte[] rowVer){
        public ContactEmailAddressRow{rowVer=rowVer==null?null:rowVer.clone();}@Override public byte[] rowVer(){return rowVer==null?null:rowVer.clone();}}
    public record ContactAddressRow(long id,String kind,String addressLine1,String addressLine2,String city,
            String stateOrProvince,String postalCode,String countryCode,String legacyAddressText,boolean primary,
            int sortOrder,boolean deleted,Instant createdAt,Instant updatedAt,byte[] rowVer){
        public ContactAddressRow{rowVer=rowVer==null?null:rowVer.clone();}@Override public byte[] rowVer(){return rowVer==null?null:rowVer.clone();}}


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
            String address,
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
                      %s,
                      %s AS CredentialAbbreviations
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC;
                    """.formatted(
                    optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                    optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
                    displayNameExpression(schema, "c"),
                    currentEmailExpression("c", schema.tenantColumn()),
                    currentPhoneExpression("c", schema.tenantColumn()),
                    credentialAbbreviationsExpression("c", schema.tenantColumn()),
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
                                rs.getString("Phone"), splitCredentialAbbreviations(rs.getString("CredentialAbbreviations"))));
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
                      %s,
                      %s AS CredentialAbbreviations
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
                    currentEmailExpression("c", schema.tenantColumn()),
                    currentPhoneExpression("c", schema.tenantColumn()),
                    credentialAbbreviationsExpression("c", schema.tenantColumn()),
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
                                rs.getString("Phone"), splitCredentialAbbreviations(rs.getString("CredentialAbbreviations"))));
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
                      %s,
                      %s AS CredentialAbbreviations
                    FROM dbo.Contacts c
                    WHERE c.Id = ?
                      AND c.%s = ?
                      AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                    %s;
                    """.formatted(
                    optionalColumnExpression(schema.firstNameColumn(), "c", "FirstName"),
                    optionalColumnExpression(schema.lastNameColumn(), "c", "LastName"),
                    displayNameExpression(schema, "c"),
                    currentEmailExpression("c", schema.tenantColumn()),
                    currentPhoneExpression("c", schema.tenantColumn()),
                    credentialAbbreviationsExpression("c", schema.tenantColumn()),
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
                            rs.getString("Phone"), splitCredentialAbbreviations(rs.getString("CredentialAbbreviations")));
                    logPerf("contacts.directory.detail.query", "contactId=" + contactId + " tenantId=" + shaleClientId + " found=true", started);
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load directory contact by id (id=" + contactId + ")", e);
        }
    }

    public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage(int shaleClientId, int actorUserId,
            int page, int pageSize, String searchQuery, DirectoryFilters filters) {
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
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
        Objects.requireNonNull(filters, "filters");

        long validationStarted = System.nanoTime();
        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);
            logPerf("contacts.directory.phase.validation", "tenantId=" + shaleClientId + " actorValidated=true", validationStarted);

            long countStarted = System.nanoTime();
            long total = countDirectoryContacts(con, schema, shaleClientId, actorUserId, searchQuery, filters);
            logPerf("contacts.directory.count", "tenantId=" + shaleClientId + " page=" + page + " queryLength=" + normalizedQueryLength(searchQuery) + " total=" + total, countStarted);
            if (total == 0) {
                logPerf("contacts.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedQueryLength(searchQuery) + " rows=0 total=0 fullDetailHydration=false selectedFields=id,displayName,email,phone,credentialAbbreviations", started);
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }

            String searchClause = structuredDirectoryPredicate(schema, filters);
            String displayName = lightweightDisplayNameExpression(schema, "c");
            String sql = """
                    SELECT
                      c.Id,
                      %s AS DisplayName,
                      (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e
                       WHERE e.ContactId=c.Id AND e.ShaleClientId=c.%s AND e.IsDeleted=0
                       ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email,
                      (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p
                       WHERE p.ContactId=c.Id AND p.ShaleClientId=c.%s AND p.IsDeleted=0
                       ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone
                    FROM dbo.Contacts c
                    WHERE c.%s = ?
                      AND %s IS NOT NULL
                      AND EXISTS (SELECT 1 FROM dbo.Users u WHERE u.Id=? AND u.ShaleClientId=c.%s
                                  AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0)
                    %s
                    %s
                    ORDER BY DisplayName ASC, c.Id ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
                    """.formatted(
                    displayName,
                    schema.tenantColumn(), schema.tenantColumn(),
                    schema.tenantColumn(),
                    displayName,
                    schema.tenantColumn(),
                    activeFilter(schema.deletedColumn(), "c"),
                    searchClause);

            record PageRow(int id, String displayName, String email, String phone) {}
            List<PageRow> selected = new ArrayList<>(pageSize);
            long selectionStarted = System.nanoTime();
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int idx = 1;
                idx = bindStructuredDirectoryQuery(ps, idx, shaleClientId, actorUserId, searchQuery, filters);
                ps.setInt(idx++, page * pageSize);
                ps.setInt(idx, pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        selected.add(new PageRow(
                                rs.getInt("Id"),
                                rs.getString("DisplayName"),
                                rs.getString("Email"),
                                rs.getString("Phone")));
                    }
                }
            }
            logPerf("contacts.directory.phase.pageSelection", "tenantId=" + shaleClientId + " page=" + page
                    + " pageSize=" + pageSize + " rows=" + selected.size(), selectionStarted);

            long enrichmentStarted = System.nanoTime();
            Map<Integer, List<String>> credentialsByContact = loadCredentialAbbreviations(con, shaleClientId,
                    selected.stream().map(PageRow::id).toList());
            logPerf("contacts.directory.phase.credentialEnrichment", "tenantId=" + shaleClientId
                    + " contactIds=" + selected.size(), enrichmentStarted);

            long mappingStarted = System.nanoTime();
            List<ContactCardSummaryRow> out = selected.stream()
                    .map(row -> new ContactCardSummaryRow(row.id(), row.displayName(), row.email(), row.phone(),
                            credentialsByContact.getOrDefault(row.id(), List.of())))
                    .toList();
            logPerf("contacts.directory.phase.resultMapping", "tenantId=" + shaleClientId + " rows=" + out.size(), mappingStarted);
            PagedResult<ContactCardSummaryRow> result = new PagedResult<>(List.copyOf(out), page, pageSize, total);
            logPerf("contacts.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedQueryLength(searchQuery) + " rows=" + out.size() + " total=" + total + " fullDetailHydration=false selectedFields=id,displayName,email,phone,credentialAbbreviations", started);
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contacts page for tenant (clientId=" + shaleClientId + ", page=" + page + ")", e);
        }
    }

    /** Enriches only the selected page, in one tenant-scoped query (never one query per card). */
    private static Map<Integer, List<String>> loadCredentialAbbreviations(Connection con, int shaleClientId,
            List<Integer> contactIds) throws SQLException {
        if (contactIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(contactIds.size(), "?"));
        String sql = """
                SELECT a.ContactId,d.Abbreviation
                FROM dbo.ContactCredentials a
                JOIN dbo.CredentialDefinitions d ON d.Id=a.CredentialDefinitionId
                  AND (d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL)
                WHERE a.ShaleClientId=? AND a.IsDeleted=0
                  AND a.ContactId IN (%s)
                  AND NULLIF(LTRIM(RTRIM(d.Abbreviation)),N'') IS NOT NULL
                ORDER BY a.ContactId,a.DisplayOrder,d.SortOrder,d.Name,d.Id,a.Id
                """.formatted(placeholders);
        Map<Integer, List<String>> mutable = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int index = 1;
            ps.setInt(index++, shaleClientId);
            for (Integer contactId : contactIds) ps.setInt(index++, contactId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mutable.computeIfAbsent(rs.getInt("ContactId"), ignored -> new ArrayList<>())
                        .add(rs.getString("Abbreviation"));
            }
        }
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        mutable.forEach((id, values) -> result.put(id, List.copyOf(values)));
        return Map.copyOf(result);
    }

    public long countDirectoryContacts(int shaleClientId, String searchQuery) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            ContactSchema schema = ContactSchema.load(con);
            logDetectedCoreColumns(schema);
            return countDirectoryContacts(con, schema, shaleClientId, requireCurrentPrincipalUserId(con),
                    searchQuery, DirectoryFilters.EMPTY);
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
                  SELECT d.Id,d.SystemKey,d.Name,d.Description,d.Color,d.SortOrder,d.IsActive,d.IsDeleted,
                    ROW_NUMBER() OVER (PARTITION BY d.SystemKey
                      ORDER BY CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END,d.Id) rn
                  FROM dbo.%s d
                  WHERE (d.ShaleClientId=? OR d.ShaleClientId IS NULL) AND d.IsDeleted=0
                )
                SELECT Id,SystemKey,Name,Description,Color,SortOrder,IsActive,IsDeleted
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
                  SELECT d.Id,d.SystemKey,d.Name,d.Abbreviation,d.Description,d.Color,d.SortOrder,d.IsActive,d.IsDeleted,
                    ROW_NUMBER() OVER (PARTITION BY d.SystemKey
                      ORDER BY CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END,d.Id) rn
                  FROM dbo.CredentialDefinitions d
                  WHERE (d.ShaleClientId=? OR d.ShaleClientId IS NULL) AND d.IsDeleted=0
                )
                SELECT Id,SystemKey,Name,Abbreviation,Description,Color,SortOrder,IsActive,IsDeleted
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

    /** Bounded Settings read; identifiers are selected only by the closed category switch. */
    public List<AdministrationDefinitionRow> listDefinitionsForAdministration(
            DefinitionCategory category, int shaleClientId, int actorUserId) {
        Objects.requireNonNull(category, "category");
        validateTenantId(shaleClientId);
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
        String table = switch (category) {
            case CONTACT_TYPE -> "ContactTypes";
            case SPECIALTY -> "Specialties";
            case CREDENTIAL -> "CredentialDefinitions";
        };
        String abbreviation = category == DefinitionCategory.CREDENTIAL
                ? "d.Abbreviation" : "CAST(NULL AS nvarchar(50))";
        String sql = "SELECT d.Id,d.ShaleClientId,d.SystemKey,d.Name," + abbreviation
                + " Abbreviation,d.Description,d.Color,d.SortOrder,d.IsActive,d.IsDeleted,d.RowVer "
                + "FROM dbo." + table + " d WHERE d.ShaleClientId IS NULL OR d.ShaleClientId=? "
                + "ORDER BY d.SortOrder,d.Name,d.Id";
        try (Connection con = db.requireConnection()) {
            verifyTenantMatchesSession(con, shaleClientId);
            try (PreparedStatement actor = con.prepareStatement("""
                    SELECT 1 FROM dbo.Users
                    WHERE id=? AND ShaleClientId=? AND ISNULL(is_admin,0)=1
                      AND ISNULL(is_deleted,0)=0 AND ISNULL(IsRemoved,0)=0
                    """)) {
                actor.setInt(1, actorUserId); actor.setInt(2, shaleClientId);
                try (ResultSet rs = actor.executeQuery()) {
                    if (!rs.next()) throw new SecurityException("An active tenant administrator is required.");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<AdministrationDefinitionRow> rows = new ArrayList<>();
                    while (rs.next()) rows.add(new AdministrationDefinitionRow(category, rs.getInt("Id"),
                            (Integer) rs.getObject("ShaleClientId"), rs.getString("SystemKey"),
                            rs.getString("Name"), rs.getString("Abbreviation"), rs.getString("Description"),
                            rs.getString("Color"), rs.getInt("SortOrder"), rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"),
                            rs.getBytes("RowVer")));
                    return List.copyOf(rows);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load Contact definition administration list.", e);
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
                             NULLIF(LTRIM(RTRIM(CONCAT(c.FirstName,' ',c.LastName))),'')) LegacyDisplayName,
                           c.DateOfBirth,c.Condition,c.IsDeceased,c.UpdatedAt
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
                    List<ContactPhoneNumberRow> phones=loadPhones(con,contactId,shaleClientId);
                    List<ContactEmailAddressRow> emails=loadEmails(con,contactId,shaleClientId);
                    List<ContactAddressRow> addresses=loadAddresses(con,contactId,shaleClientId);
                    return new ClassificationProfileRow(contactId, shaleClientId, prefix, first, middle, last,
                            preferred, suffix, display, rs.getDate("DateOfBirth")==null?null:rs.getDate("DateOfBirth").toLocalDate(),
                            rs.getString("Condition"),rs.getBoolean("IsDeceased"),rs.getTimestamp("UpdatedAt")==null?null:rs.getTimestamp("UpdatedAt").toInstant(), types, specialties, credentials,phones,emails,addresses);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load Contact classification profile (id=" + contactId + ")", e);
        }
    }

    private static Instant instant(ResultSet r,String c)throws SQLException{Timestamp t=r.getTimestamp(c);return t==null?null:t.toInstant();}
    private static List<ContactPhoneNumberRow> loadPhones(Connection c,int contact,int tenant)throws SQLException{var out=new ArrayList<ContactPhoneNumberRow>();try(var p=c.prepareStatement("SELECT Id,Kind,DisplayNumber,NormalizedNumber,Extension,IsPrimary,SortOrder,IsDeleted,CreatedAt,UpdatedAt,RowVer FROM dbo.ContactPhoneNumbers WHERE ContactId=? AND ShaleClientId=? ORDER BY IsDeleted,IsPrimary DESC,SortOrder,Id")){p.setInt(1,contact);p.setInt(2,tenant);try(var r=p.executeQuery()){while(r.next())out.add(new ContactPhoneNumberRow(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6),r.getInt(7),r.getBoolean(8),instant(r,"CreatedAt"),instant(r,"UpdatedAt"),r.getBytes(11)));}}return List.copyOf(out);}
    private static List<ContactEmailAddressRow> loadEmails(Connection c,int contact,int tenant)throws SQLException{var out=new ArrayList<ContactEmailAddressRow>();try(var p=c.prepareStatement("SELECT Id,Kind,EmailAddress,NormalizedEmail,IsPrimary,SortOrder,IsDeleted,CreatedAt,UpdatedAt,RowVer FROM dbo.ContactEmailAddresses WHERE ContactId=? AND ShaleClientId=? ORDER BY IsDeleted,IsPrimary DESC,SortOrder,Id")){p.setInt(1,contact);p.setInt(2,tenant);try(var r=p.executeQuery()){while(r.next())out.add(new ContactEmailAddressRow(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getInt(6),r.getBoolean(7),instant(r,"CreatedAt"),instant(r,"UpdatedAt"),r.getBytes(10)));}}return List.copyOf(out);}
    private static List<ContactAddressRow> loadAddresses(Connection c,int contact,int tenant)throws SQLException{var out=new ArrayList<ContactAddressRow>();try(var p=c.prepareStatement("SELECT Id,Kind,AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,CountryCode,LegacyAddressText,IsPrimary,SortOrder,IsDeleted,CreatedAt,UpdatedAt,RowVer FROM dbo.ContactAddresses WHERE ContactId=? AND ShaleClientId=? ORDER BY IsDeleted,IsPrimary DESC,SortOrder,Id")){p.setInt(1,contact);p.setInt(2,tenant);try(var r=p.executeQuery()){while(r.next())out.add(new ContactAddressRow(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getBoolean(10),r.getInt(11),r.getBoolean(12),instant(r,"CreatedAt"),instant(r,"UpdatedAt"),r.getBytes(15)));}}return List.copyOf(out);}

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
                    replaceBasicStructuredPoints(con, request.contactId(), request.shaleClientId(), request.actorUserId(),
                            request.email(), request.phone(), request.address());
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
                    replaceBasicStructuredPoints(con, contactId, request.shaleClientId(), request.actorUserId(),
                            request.email(), request.phone(), request.address());
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

    /**
     * Transitional basic-profile callers still supply one current value per category, but those
     * values are authoritative structured rows. This method deliberately has no dbo.Contacts
     * projection or column discovery.
     */
    private static void replaceBasicStructuredPoints(Connection con, int contactId, int tenantId,
            Integer actorUserId, String email, String phone, String address) throws SQLException {
        replaceBasicStructuredPoint(con, "ContactEmailAddresses", "EmailAddress,NormalizedEmail", contactId,
                tenantId, actorUserId, "PERSONAL", normalizeOptional(email),
                normalizeOptional(email) == null ? null : normalizeOptional(email).toLowerCase(Locale.ROOT));
        String normalizedPhone = normalizeOptional(phone) == null ? null
                : normalizeOptional(phone).replaceAll("[^0-9+]", "");
        replaceBasicStructuredPoint(con, "ContactPhoneNumbers", "DisplayNumber,NormalizedNumber", contactId,
                tenantId, actorUserId, "MOBILE", normalizeOptional(phone), normalizedPhone);
        replaceBasicStructuredPoint(con, "ContactAddresses", "LegacyAddressText", contactId,
                tenantId, actorUserId, "HOME", normalizeOptional(address));
    }

    private static void replaceBasicStructuredPoint(Connection con, String table, String valueColumns,
            int contactId, int tenantId, Integer actorUserId, String kind, String... values) throws SQLException {
        String remove = "UPDATE dbo." + table + " SET IsDeleted=1,IsPrimary=0,DeletedAt=SYSUTCDATETIME(),"
                + "DeletedByUserId=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? "
                + "WHERE ContactId=? AND ShaleClientId=? AND Kind=? AND IsDeleted=0";
        try (PreparedStatement ps = con.prepareStatement(remove)) {
            if (actorUserId == null) { ps.setNull(1, Types.INTEGER); ps.setNull(2, Types.INTEGER); }
            else { ps.setInt(1, actorUserId); ps.setInt(2, actorUserId); }
            ps.setInt(3, contactId); ps.setInt(4, tenantId); ps.setString(5, kind); ps.executeUpdate();
        }
        if (values.length == 0 || values[0] == null) return;
        String placeholders = String.join(",", Collections.nCopies(values.length, "?"));
        String insert = "INSERT dbo." + table + " (ShaleClientId,ContactId,Kind," + valueColumns
                + ",IsPrimary,SortOrder,CreatedByUserId) VALUES (?,?,?," + placeholders + ",1,0,?)";
        try (PreparedStatement ps = con.prepareStatement(insert)) {
            int i=1; ps.setInt(i++,tenantId); ps.setInt(i++,contactId); ps.setString(i++,kind);
            for (String value : values) ps.setString(i++, value);
            if (actorUserId == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, actorUserId);
            ps.executeUpdate();
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
                currentEmailExpression("c", schema.tenantColumn()),
                currentPhoneExpression("c", schema.tenantColumn()),
                currentAddressExpression("c", schema.tenantColumn()),
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
                        rs.getString("Address"),
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
                rs.getString("Description"), rs.getString("Color"), rs.getInt("SortOrder"), rs.getBoolean("IsActive"),
                rs.getBoolean("IsDeleted"));
    }

    private static CredentialDefinitionRow mapCredentialDefinition(ResultSet rs) throws SQLException {
        return new CredentialDefinitionRow(rs.getInt("Id"), rs.getString("SystemKey"), rs.getString("Name"),
                rs.getString("Abbreviation"), rs.getString("Description"), rs.getString("Color"), rs.getInt("SortOrder"),
                rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"));
    }

    private static List<AssignedDefinitionRow> loadAssignedDefinitions(Connection con, String assignmentTable,
            String definitionIdColumn, String definitionTable, int contactId, int shaleClientId) throws SQLException {
        String sql = """
                SELECT a.Id AssignmentId,a.RowVer,d.Id,d.SystemKey,d.Name,d.Description,d.Color,d.SortOrder,d.IsActive,d.IsDeleted
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
                            !definition.active() || definition.deleted(),rs.getBytes("RowVer")));
                }
                return rows;
            }
        }
    }

    private static List<AssignedCredentialRow> loadAssignedCredentials(Connection con, int contactId,
            int shaleClientId) throws SQLException {
        String sql = """
                SELECT a.Id AssignmentId,a.DisplayOrder,a.RowVer,d.Id,d.SystemKey,d.Name,d.Abbreviation,
                       d.Description,d.Color,d.SortOrder,d.IsActive,d.IsDeleted
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
                            rs.getInt("DisplayOrder"), !definition.active() || definition.deleted(),rs.getBytes("RowVer")));
                }
                return rows;
            }
        }
    }

    /** A correlated projection keeps directory loading bounded to its existing query. */
    private static String credentialAbbreviationsExpression(String contactAlias, String tenantColumn) {
        return "(SELECT STRING_AGG(CONVERT(nvarchar(max), x.Abbreviation), NCHAR(31)) "
                + "WITHIN GROUP (ORDER BY x.DisplayOrder, x.SortOrder, x.Name, x.Id) FROM ("
                + "SELECT d.Abbreviation,a.DisplayOrder,d.SortOrder,d.Name,d.Id "
                + "FROM dbo.ContactCredentials a JOIN dbo.CredentialDefinitions d "
                + "ON d.Id=a.CredentialDefinitionId AND (d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL) "
                + "WHERE a.ContactId=" + contactAlias + ".Id AND a.ShaleClientId=" + contactAlias + "." + tenantColumn + " "
                // Definition lifecycle does not hide an existing assignment. This deliberately
                // matches loadAssignedCredentials, including tenant-owned and global definitions.
                + "AND a.IsDeleted=0 "
                + "AND NULLIF(LTRIM(RTRIM(d.Abbreviation)),N'') IS NOT NULL "
                + ") x)";
    }

    private static List<String> splitCredentialAbbreviations(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\u001f", -1));
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

    private static int requireCurrentPrincipalUserId(Connection con) throws SQLException {
        try (PreparedStatement ps=con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'PrincipalUserId') AS INT)");
             ResultSet rs=ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1)<=0) throw new IllegalStateException("PrincipalUserId session context is missing.");
            return rs.getInt(1);
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
        return "COALESCE(NULLIF(LTRIM(RTRIM(" + name + ")),''),NULLIF(LTRIM(RTRIM("
                + "COALESCE(" + first + ", '') + CASE WHEN COALESCE(" + first + ", '') = '' OR COALESCE(" + last + ", '') = '' THEN '' ELSE ' ' END + COALESCE(" + last + ", '')" + ")),''))";
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

    /**
     * Authoritative current contact-point projections.  These deliberately have no
     * scalar Contacts-column fallback: an empty structured aggregate is an empty
     * current value.  Id is the final tie-breaker so corrupt/pre-constraint data is
     * still presented deterministically.
     */
    private static String currentPhoneExpression(String alias, String tenantColumn) {
        return "(SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p "
                + "WHERE p.ContactId=" + alias + ".Id AND p.ShaleClientId=" + alias + "." + tenantColumn
                + " AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone";
    }

    private static String currentEmailExpression(String alias, String tenantColumn) {
        return "(SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e "
                + "WHERE e.ContactId=" + alias + ".Id AND e.ShaleClientId=" + alias + "." + tenantColumn
                + " AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email";
    }

    private static String currentAddressExpression(String alias, String tenantColumn) {
        return "(SELECT TOP(1) COALESCE(NULLIF(LTRIM(RTRIM(a.LegacyAddressText)),N''),"
                + "NULLIF(LTRIM(RTRIM(CONCAT(a.AddressLine1,CASE WHEN NULLIF(a.AddressLine2,N'') IS NULL THEN N'' ELSE N', '+a.AddressLine2 END,"
                + "CASE WHEN NULLIF(a.City,N'') IS NULL THEN N'' ELSE N', '+a.City END,"
                + "CASE WHEN NULLIF(a.StateOrProvince,N'') IS NULL THEN N'' ELSE N', '+a.StateOrProvince END,"
                + "CASE WHEN NULLIF(a.PostalCode,N'') IS NULL THEN N'' ELSE N' '+a.PostalCode END,"
                + "CASE WHEN NULLIF(a.CountryCode,N'') IS NULL THEN N'' ELSE N', '+a.CountryCode END))),N'')) "
                + "FROM dbo.ContactAddresses a WHERE a.ContactId=" + alias + ".Id AND a.ShaleClientId=" + alias + "." + tenantColumn
                + " AND a.IsDeleted=0 ORDER BY a.IsPrimary DESC,a.SortOrder,a.Id) AS Address";
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
                "COALESCE((SELECT TOP(1) e.NormalizedEmail FROM dbo.ContactEmailAddresses e WHERE e.ContactId=" + alias + ".Id AND e.ShaleClientId=" + alias + "." + schema.tenantColumn() + " AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id),N'')",
                "COALESCE((SELECT TOP(1) p.NormalizedNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=" + alias + ".Id AND p.ShaleClientId=" + alias + "." + schema.tenantColumn() + " AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id),N'')");
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

    private static long countDirectoryContacts(Connection con, ContactSchema schema, int shaleClientId,
                                               int actorUserId, String searchQuery, DirectoryFilters filters) throws SQLException {
        String sql = """
                SELECT COUNT_BIG(*)
                FROM dbo.Contacts c
                WHERE c.%s = ?
                  AND NULLIF(LTRIM(RTRIM(%s)), '') IS NOT NULL
                  AND EXISTS (SELECT 1 FROM dbo.Users u WHERE u.Id=? AND u.ShaleClientId=c.%s
                              AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0)
                %s
                %s;
                """.formatted(
                schema.tenantColumn(),
                displayNameExpression(schema, "c"),
                schema.tenantColumn(),
                activeFilter(schema.deletedColumn(), "c"),
                structuredDirectoryPredicate(schema, filters));

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindStructuredDirectoryQuery(ps, 1, shaleClientId, actorUserId, searchQuery, filters);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getLong(1);
            }
        }
    }

    /** One predicate/query for names, active assignments and active structured contact points. */
    private static String structuredDirectoryPredicate(ContactSchema schema, DirectoryFilters filters) {
        // DisplayName is not present in every supported Contacts schema (older production
        // tenants use Name/FullName).  Keep this expression schema-aware just like the
        // projection; a literal c.DisplayName here caused the count to fail before paging.
        StringBuilder sql = new StringBuilder("""
              AND (? = '' OR
                LOWER(COALESCE(%s,N'')) LIKE ? ESCAPE N'\\' OR LOWER(COALESCE(c.Prefix,N'')) LIKE ? ESCAPE N'\\' OR
                LOWER(COALESCE(c.FirstName,N'')) LIKE ? ESCAPE N'\\' OR LOWER(COALESCE(c.MiddleName,N'')) LIKE ? ESCAPE N'\\' OR
                LOWER(COALESCE(c.LastName,N'')) LIKE ? ESCAPE N'\\' OR LOWER(COALESCE(c.PreferredName,N'')) LIKE ? ESCAPE N'\\' OR
                LOWER(COALESCE(c.Suffix,N'')) LIKE ? ESCAPE N'\\' OR
                EXISTS(SELECT 1 FROM dbo.ContactContactTypes a JOIN dbo.ContactTypes d ON d.Id=a.ContactTypeId
                  WHERE a.ContactId=c.Id AND a.ShaleClientId=c.ShaleClientId AND a.IsDeleted=0 AND d.IsActive=1 AND d.IsDeleted=0 AND LOWER(d.Name) LIKE ? ESCAPE N'\\') OR
                EXISTS(SELECT 1 FROM dbo.ContactSpecialties a JOIN dbo.Specialties d ON d.Id=a.SpecialtyId
                  WHERE a.ContactId=c.Id AND a.ShaleClientId=c.ShaleClientId AND a.IsDeleted=0 AND d.IsActive=1 AND d.IsDeleted=0 AND LOWER(d.Name) LIKE ? ESCAPE N'\\') OR
                EXISTS(SELECT 1 FROM dbo.ContactCredentials a JOIN dbo.CredentialDefinitions d ON d.Id=a.CredentialDefinitionId
                  WHERE a.ContactId=c.Id AND a.ShaleClientId=c.ShaleClientId AND a.IsDeleted=0 AND d.IsActive=1 AND d.IsDeleted=0
                    AND (LOWER(d.Name) LIKE ? ESCAPE N'\\' OR LOWER(d.Abbreviation) LIKE ? ESCAPE N'\\')) OR
                EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=c.Id AND p.ShaleClientId=c.ShaleClientId AND p.IsDeleted=0
                    AND (LOWER(p.DisplayNumber) LIKE ? ESCAPE N'\\' OR (?<>'' AND p.NormalizedNumber LIKE ? ESCAPE N'\\'))) OR
                EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ContactId=c.Id AND e.ShaleClientId=c.ShaleClientId AND e.IsDeleted=0
                    AND (LOWER(e.EmailAddress) LIKE ? ESCAPE N'\\' OR LOWER(e.NormalizedEmail) LIKE ? ESCAPE N'\\')) OR
                EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ContactId=c.Id AND a.ShaleClientId=c.ShaleClientId AND a.IsDeleted=0 AND
                    LOWER(CONCAT(a.AddressLine1,N' ',a.AddressLine2,N' ',a.City,N' ',a.StateOrProvince,N' ',a.PostalCode,N' ',a.CountryCode,N' ',a.LegacyAddressText)) LIKE ? ESCAPE N'\\'))
            """.formatted(lightweightDisplayNameExpression(schema, "c")));
        appendIdFilter(sql, filters.contactTypeIds(), "ContactContactTypes", "ContactTypeId");
        appendIdFilter(sql, filters.specialtyIds(), "ContactSpecialties", "SpecialtyId");
        appendIdFilter(sql, filters.credentialIds(), "ContactCredentials", "CredentialDefinitionId");
        return sql.toString();
    }

    private static void appendIdFilter(StringBuilder sql, List<Integer> ids, String table, String column) {
        if (ids.isEmpty()) return;
        sql.append(" AND EXISTS(SELECT 1 FROM dbo.").append(table).append(" f WHERE f.ContactId=c.Id AND f.ShaleClientId=c.ShaleClientId AND f.IsDeleted=0 AND f.")
                .append(column).append(" IN (").append(String.join(",", java.util.Collections.nCopies(ids.size(), "?"))).append("))");
    }

    private static int bindStructuredDirectoryQuery(PreparedStatement ps, int idx, int tenant, int actor,
            String query, DirectoryFilters filters) throws SQLException {
        ps.setInt(idx++, tenant); ps.setInt(idx++, actor);
        String normalized = normalizeSearchQuery(query);
        String like = likeParameter(escapeLike(normalized));
        ps.setString(idx++, normalized);
        for (int i=0;i<12;i++) ps.setString(idx++, like);
        String digits=normalizePhoneDigits(query);
        ps.setString(idx++, digits); ps.setString(idx++, likeParameter(escapeLike(digits)));
        for (int i=0;i<3;i++) ps.setString(idx++, like);
        for (Integer id : filters.contactTypeIds()) ps.setInt(idx++, id);
        for (Integer id : filters.specialtyIds()) ps.setInt(idx++, id);
        for (Integer id : filters.credentialIds()) ps.setInt(idx++, id);
        return idx;
    }

    static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\["); }

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
                    existingColumn(con, "Contacts", List.of("DisplayName", "Name", "FullName", "name")),
                    existingColumn(con, "Contacts", List.of("FirstName", "NameFirst", "name_first", "first_name")),
                    existingColumn(con, "Contacts", List.of("LastName", "NameLast", "name_last", "last_name")),
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
    public void updateContactProfile(com.shale.core.service.ContactServicePort.UpdateContactProfileCommand c){mutationDao.aggregate(c);}

}
