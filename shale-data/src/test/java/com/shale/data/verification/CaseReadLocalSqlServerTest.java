package com.shale.data.verification;

import static com.shale.data.verification.CaseReadVerificationSafety.require;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.shale.core.dto.MinimizedCaseOverview;
import com.shale.core.dto.MinimizedCasePage;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseReadException;
import com.shale.core.service.CaseServicePort;
import com.shale.core.semantics.RoleSemantics;
import com.shale.data.dao.CaseDao;
import com.shale.data.runtime.RuntimeSessionService;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Opt-in real Microsoft JDBC/SQL Server verification. All controls are test-scope only.
 * Requires reviewed schema-only definitions, never a mock replacement for authoritative RLS.
 * Does not run an HTTP server, authenticate a bearer, or certify deployed Azure policy/performance.
 */
class CaseReadLocalSqlServerTest {
    private static final List<String> READ_TABLES = List.of("Cases", "Users", "CaseUsers", "CaseStatuses",
            "Statuses", "PracticeAreas", "CaseDates", "CaseDateTypes", "CaseDateTypeSemanticRoleMappings");
    private static final List<String> STRICT = List.of("Cases", "Users", "CaseUsers", "CaseDates", "AuditLog");
    private static final List<String> OVERLAY = List.of("Statuses", "PracticeAreas", "CaseDateTypes", "CaseDateTypeSemanticRoleMappings");
    private final String runId = UUID.randomUUID().toString().replace("-", "");
    private final String database = "ShaleCaseReadVerify_" + runId;
    private final String login = "CaseReadVerify_" + runId;
    private final String password = "V!9a" + UUID.randomUUID() + UUID.randomUUID();
    private final String marker = UUID.randomUUID().toString();
    private int port;
    private SQLServerDataSource admin;
    private boolean createdDatabase, createdLogin, marked;
    private HikariDataSource pool;
    private RuntimeSessionService session;
    private CaseServicePort service;
    private String phase = "prerequisites";
    private String fixtureStep = "not_started";
    private final List<Long> aCases = new ArrayList<>(), bCases = new ArrayList<>();
    private long aDeleted, bDeleted, unassigned, closed;
    private int tenantA, tenantB, actorA, actorA2, actorB, removedA, inactiveA;
    private int areaGlobal, areaA, areaB, statusGlobal, statusA, statusB, statusClosed;
    private long typeGlobal, typeA, typeB;
    private int spid;

    @Test void selectedProductionReadsOnAnOwnedDisposableLocalDatabase() {
        assumeTrue(System.getenv("SHALE_CASE_VERIFY_OPT_IN") != null,
                "NOT RUN: use build/case-read-verification/run.py with explicit local opt-in and reviewed DDL");
        Throwable failure = null;
        try {
            var properties = new HashMap<String, String>();
            System.getProperties().forEach((k, v) -> properties.put(k.toString(), v.toString()));
            port = CaseReadVerificationSafety.configuration(System.getenv(), properties);
            List<String> schema = CaseReadVerificationSafety.schema(Path.of(env("SCHEMA")), env("SCHEMA_SHA256"));
            setup(schema);
            phase = "fixtures";
            fixtures();
            runtimePool();
            pass("fixtures");
            scenario("pooled_context", this::pooledContext);
            scenario("rls_enforcement", this::rlsEnforcement);
            scenario("search_assigned_overview", this::selectedReads);
            scenario("foreign_join_protection", this::foreignJoinProtection);
            scenario("actor_authority", this::actorAuthority);
            scenario("overview_audit", this::overviewAudit);
            scenario("audit_insert_denied", this::auditDenied);
            scenario("failure_before_commit", () -> commitFault(Fault.BEFORE_COMMIT, 0));
            scenario("commit_lost_ack", () -> commitFault(Fault.AFTER_COMMIT, 1));
        } catch (Throwable caught) {
            failure = caught;
            System.out.println("CASE_READ_VERIFY FAIL " + phase);
        } finally {
            try { cleanup(); }
            catch (Throwable cleanupFailure) {
                System.out.println("CASE_READ_VERIFY FAIL cleanup");
                if (failure == null) failure = cleanupFailure;
            }
        }
        // No cause/message/DTO/SQL/credential appears in Surefire reports, even for setup errors.
        if (failure != null) throw new AssertionError("Local SQL verification failed in " + phase
                + "; exceptionClass=" + failure.getClass().getName()
                + "; fixtureStep=" + fixtureStep
                + "; sqlError=" + (failure instanceof SQLException sql ? sql.getErrorCode() : 0)
                + "; inspect runbook prerequisites");
    }

    private static String env(String suffix) { return System.getenv("SHALE_CASE_VERIFY_" + suffix); }
    private void scenario(String name, Checked work) throws Exception { phase = name; work.run(); pass(name); }
    private static void pass(String name) { System.out.println("CASE_READ_VERIFY PASS " + name); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private SQLServerDataSource source(String db, String user, String secret) {
        var ds = new SQLServerDataSource();
        ds.setURL(CaseReadVerificationSafety.url(port, db));
        ds.setUser(user); ds.setPassword(secret); ds.setLoginTimeout(5);
        return ds;
    }

    private Connection adminConnection(String db) throws SQLException {
        var con = source(db, env("ADMIN_USER"), env("ADMIN_PASSWORD")).getConnection();
        try { identity(con, db); return con; }
        catch (Throwable failure) { con.close(); throw failure; }
    }

    private void identity(Connection con, String expectedDb) throws SQLException {
        try (var ps = con.prepareStatement("SELECT DB_NAME(),CONVERT(int,SERVERPROPERTY('EngineEdition'))")) {
            ps.setQueryTimeout(5);
            try (var rs = ps.executeQuery()) {
                require(rs.next() && expectedDb.equals(rs.getString(1)));
                require(Set.of(2, 3, 4).contains(rs.getInt(2))); // local Standard/Enterprise/Express only; reject Azure
            }
        }
    }

    private void setup(List<String> ddl) throws Exception {
        phase = "setup";
        admin = source("master", env("ADMIN_USER"), env("ADMIN_PASSWORD"));
        try (var con = adminConnection("master")) {
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.databases WHERE name=?", database) == 0);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.server_principals WHERE name=?", login) == 0);
            execute(con, "CREATE DATABASE [" + database + "]");
            createdDatabase = true;
        }
        try (var con = adminConnection(database)) {
            execute(con, "CREATE TABLE dbo.CaseReadHarnessMarker(RunId varchar(32) NOT NULL, Nonce varchar(36) NOT NULL, SchemaHash char(64) NOT NULL)");
            update(con, "INSERT dbo.CaseReadHarnessMarker VALUES(?,?,?)", runId, marker, env("SCHEMA_SHA256"));
            marked = true;
            owned(con);
        }
        // Fresh login has no server role and no access to other business databases. Import happens
        // as db_owner of ONLY this marked database, never with the local administrator connection.
        try (var con = adminConnection("master")) {
            // SQL Server CREATE LOGIN does not accept a password parameter. Generated UUID-only
            // secret is held in memory and never logged; there is no user string interpolation here.
            execute(con, "CREATE LOGIN [" + login + "] WITH PASSWORD='" + password + "',CHECK_POLICY=OFF,DEFAULT_DATABASE=[" + database + "]");
            createdLogin = true;
        }
        try (var con = adminConnection(database)) {
            owned(con);
            execute(con, "CREATE USER [" + login + "] FOR LOGIN [" + login + "]");
            execute(con, "ALTER ROLE db_owner ADD MEMBER [" + login + "]");
        }
        try (var con = source(database, login, password).getConnection()) {
            identity(con, database); owned(con);
            for (String batch : ddl) execute(con, batch);
        } finally {
            try (var con = adminConnection(database)) {
                owned(con);
                execute(con, "ALTER ROLE db_owner DROP MEMBER [" + login + "]");
            }
        }
        try (var con = adminConnection(database)) {
            owned(con);
            // Reject all data in the imported schema, even if an RLS-filtered SELECT would hide it.
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.partitions p JOIN sys.tables t ON t.object_id=p.object_id "
                    + "WHERE p.index_id IN(0,1) AND p.rows>0 AND t.name<>'CaseReadHarnessMarker'") == 0);
            for (String table : READ_TABLES) require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.tables WHERE object_id=OBJECT_ID(?)", "dbo." + table) == 1);
            for (String table : concat(STRICT, OVERLAY)) {
                String expected = STRICT.contains(table) ? "fn_FilterByTenant" : "fn_FilterByTenantOrGlobal";
                require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id "
                        + "WHERE p.target_object_id=OBJECT_ID(?) AND p.predicate_type_desc='FILTER' AND s.name='TenantFilter' AND s.is_enabled=1 "
                        + "AND LOWER(REPLACE(REPLACE(REPLACE(p.predicate_definition,'[',''),']',''),' ','')) IN (?,?)",
                        "dbo." + table, "sec." + expected.toLowerCase(Locale.ROOT) + "(shaleclientid)",
                        "(sec." + expected.toLowerCase(Locale.ROOT) + "(shaleclientid))") == 1);
            }
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1") == 0);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1") == 0);
            for (String table : READ_TABLES) execute(con, "GRANT SELECT ON dbo.[" + table + "] TO [" + login + "]");
            execute(con, "GRANT INSERT ON dbo.AuditLog TO [" + login + "]");
            // Test-only inspection grant, not a proposal for ordinary-user production audit grants.
            execute(con, "GRANT SELECT ON dbo.AuditLog TO [" + login + "]");
        }
        pass("setup");
    }

    private static List<String> concat(List<String> a, List<String> b) {
        var result = new ArrayList<>(a); result.addAll(b); return result;
    }

    private void owned(Connection con) throws SQLException {
        require(createdDatabase && marked);
        identity(con, database);
        require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.CaseReadHarnessMarker WHERE RunId=? AND Nonce=? AND SchemaHash=?",
                runId, marker, env("SCHEMA_SHA256")) == 1);
        require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.CaseReadHarnessMarker") == 1);
    }

    private void fixtures() throws Exception {
        try (var con = adminConnection(database)) {
            owned(con);
            tenantA = (int) insert(con, "ShaleClients", values("Code", "verify_a", "DisplayName", "Synthetic A", "City", "Local", "StateProvince", "CO", "PostalCode", "00000", "IsActive", true));
            tenantB = (int) insert(con, "ShaleClients", values("Code", "verify_b", "DisplayName", "Synthetic B", "City", "Local", "StateProvince", "CO", "PostalCode", "00000", "IsActive", true));
            context(con, tenantA, null);
            actorA = user(con, tenantA, "A1"); actorA2 = user(con, tenantA, "A2");
            removedA = user(con, tenantA, "Removed"); inactiveA = user(con, tenantA, "Inactive");
            update(con, "UPDATE dbo.Users SET is_deleted=1,IsRemoved=1,RemovedAt=SYSUTCDATETIME(),RemovedByUserId=? WHERE id=? AND ShaleClientId=?", actorA, removedA, tenantA);
            update(con, "UPDATE dbo.Users SET is_deleted=1 WHERE id=? AND ShaleClientId=?", inactiveA, tenantA);
            context(con, tenantB, null); actorB = user(con, tenantB, "B1");
            context(con, null, null);
            explicitInsert(con, "Roles", values("Id", RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY, "Name", "responsible_attorney", "IsActive", true, "ShaleClientId", tenantA));
            explicitInsert(con, "Roles", values("Id", RoleSemantics.ROLE_LEGAL_ASSISTANT, "Name", "legal_assistant", "IsActive", true, "ShaleClientId", tenantA));
            if (scalar(con, "SELECT COUNT_BIG(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.ObjectTypes')") == 1)
                explicitInsert(con, "ObjectTypes", values("Id", 1, "Name", "Case"));
            areaGlobal = area(con, null, "Global"); statusGlobal = status(con, null, "Global", false);
            typeGlobal = dateType(con, null, "global_intake");
            explicitInsert(con, "CaseDateSemanticRoles", values("RoleKey", "INTAKE", "IsProtected", true));
            insert(con, "CaseDateTypeSemanticRoleMappings", values("SemanticRoleKey", "INTAKE", "CaseDateTypeId", typeGlobal, "IsActive", true, "IsDeleted", false));
            context(con, tenantA, actorA);
            areaA = area(con, tenantA, "A"); statusA = status(con, tenantA, "A", false);
            statusClosed = status(con, tenantA, "Closed", true); typeA = dateType(con, tenantA, "a_intake");
            insert(con, "CaseDateTypeSemanticRoleMappings", values("ShaleClientId", tenantA, "SemanticRoleKey", "INTAKE", "CaseDateTypeId", typeA, "IsActive", true, "IsDeleted", false));
            // A long unfiltered prefix must not consume the match window.
            for (int i = 0; i < 35; i++) caseRow(con, tenantA, "Alpha nonmatch " + i, false, areaA);
            for (int i = 0; i < 27; i++) {
                long id = caseRow(con, tenantA, String.format(Locale.ROOT, "Verify matched %02d", i), false, areaA);
                aCases.add(id); assignment(con, tenantA, id, actorA, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
                assignment(con, tenantA, id, actorA2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
                caseStatus(con, id, i % 2 == 0 ? statusGlobal : statusA);
                insert(con, "CaseDates", values("ShaleClientId", tenantA, "CaseId", id, "CaseDateTypeId", i % 2 == 0 ? typeGlobal : typeA,
                        "StartsAt", Timestamp.valueOf("2026-10-12 12:00:00"), "AllDay", false, "IsDeleted", false, "CreatedByUserId", actorA));
            }
            unassigned = caseRow(con, tenantA, "Verify unassigned", false, areaA);
            closed = caseRow(con, tenantA, "Verify closed", false, areaA);
            caseStatus(con, closed, statusClosed); assignment(con, tenantA, closed, actorA, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
            aDeleted = caseRow(con, tenantA, "Verify matched 99 deleted", true, areaA);
            assignment(con, tenantA, aDeleted, actorA, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
            caseRow(con, tenantA, "Literal résumé漢字%_[", false, areaA);
            context(con, tenantB, actorB);
            areaB = area(con, tenantB, "B"); statusB = status(con, tenantB, "B", false); typeB = dateType(con, tenantB, "b_intake");
            insert(con, "CaseDateTypeSemanticRoleMappings", values("ShaleClientId", tenantB, "SemanticRoleKey", "INTAKE", "CaseDateTypeId", typeB, "IsActive", true, "IsDeleted", false));
            for (int i = 0; i < 27; i++) {
                long id = caseRow(con, tenantB, String.format(Locale.ROOT, "Verify matched %02d", i), false, areaB);
                bCases.add(id); assignment(con, tenantB, id, actorB, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
                caseStatus(con, id, statusB);
                insert(con, "CaseDates", values("ShaleClientId", tenantB, "CaseId", id, "CaseDateTypeId", typeB,
                        "StartsAt", Timestamp.valueOf("2026-10-12 12:00:00"), "AllDay", false, "IsDeleted", false, "CreatedByUserId", actorB));
            }
            bDeleted = caseRow(con, tenantB, "Verify matched 99 deleted", true, areaB);
        }
    }

    private int user(Connection con, int tenant, String name) throws SQLException {
        return (int) insert(con, "Users", values("ShaleClientId", tenant, "name_first", "Synthetic", "name_last", name,
                "email", name + "@example.invalid",
                "password_hash", "unusable-synthetic-hash", "password_alg", "bcrypt", "is_attorney", false,
                "is_admin", false, "is_deleted", false, "IsRemoved", false, "created_at", Timestamp.valueOf("2026-10-12 12:00:00")));
    }
    private int area(Connection con, Integer tenant, String name) throws SQLException {
        return (int) insert(con, "PracticeAreas", values("ShaleClientId", tenant, "Name", "Synthetic " + name,
                "Color", "#6C757D", "SystemKey", "verify_" + name.toLowerCase(Locale.ROOT), "IsActive", true, "IsDeleted", false));
    }
    private int status(Connection con, Integer tenant, String name, boolean isClosed) throws SQLException {
        return (int) insert(con, "Statuses", values("ShaleClientId", tenant, "Name", "Synthetic " + name,
                "Color", "#6C757D", "SortOrder", 1, "SystemKey", "verify_" + name.toLowerCase(Locale.ROOT),
                "LifecycleKey", isClosed ? "closed" : null, "IsClosed", isClosed, "IsActive", true, "IsDeleted", false));
    }
    private long dateType(Connection con, Integer tenant, String key) throws SQLException {
        return insert(con, "CaseDateTypes", values("ShaleClientId", tenant, "Name", "Synthetic Intake", "SystemKey", key,
                "Color", "#6C757D", "CalendarCategory", "MILESTONE", "SupportsTime", true, "IsActive", true, "IsDeleted", false));
    }
    private long caseRow(Connection con, int tenant, String name, boolean deleted, int area) throws SQLException {
        return insert(con, "Cases", values("ShaleClientId", tenant, "Name", name, "CaseNumber", "SYNTHETIC", "PracticeAreaId", area,
                "ClientEstate", false, "FollowUpMeetWithClient", false, "FollowUpNurseReview", false,
                "FollowUpExpertReview", false, "FollowUpCaseTransferred", false, "AcceptedChronology", false,
                "AcceptedConsultantExpertSearch", false, "AcceptedTestifyingExpertSearch", false,
                "AcceptedMedicalLiterature", false, "DeniedChronology", false, "FeeAgreementSigned", false,
                "MedicalRecordsRequested", false, "IsDeleted", deleted,
                "CreatedAt", Timestamp.valueOf("2026-10-12 12:00:00"), "UpdatedAt", Timestamp.valueOf("2026-10-12 12:00:00")));
    }
    private void assignment(Connection con, int tenant, long id, int actor, int role) throws SQLException {
        insert(con, "CaseUsers", values("ShaleClientId", tenant, "CaseId", id, "UserId", actor, "RoleId", role,
                "IsPrimary", true, "CreatedAt", Timestamp.valueOf("2026-10-12 12:00:00"), "UpdatedAt", Timestamp.valueOf("2026-10-12 12:00:00")));
    }
    private void caseStatus(Connection con, long id, int status) throws SQLException {
        insert(con, "CaseStatuses", values("CaseId", id, "StatusId", status, "EffectiveDate", Timestamp.valueOf("2026-10-12 12:00:00"),
                "IsPrimary", true, "CreatedAt", Timestamp.valueOf("2026-10-12 12:00:00"), "UpdatedAt", Timestamp.valueOf("2026-10-12 12:00:00")));
    }

    private static LinkedHashMap<String, Object> values(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private long insert(Connection con, String table, LinkedHashMap<String, Object> fields) throws SQLException {
        fixtureStep = switch (table) {
            case "ShaleClients", "Users", "Roles", "PracticeAreas", "Statuses", "CaseDateTypes",
                    "CaseDateSemanticRoles", "CaseDateTypeSemanticRoleMappings", "Cases", "CaseUsers",
                    "CaseStatuses", "CaseDates", "ObjectTypes" -> table;
            default -> "other";
        };
        require(table.matches("[A-Za-z]+") && fields.keySet().stream().allMatch(k -> k.matches("[A-Za-z_]+")));
        for (String field : fields.keySet())
            require(scalar(con, "SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(?) AND name=?", "dbo." + table, field) == 1);
        String sql = "INSERT dbo.[" + table + "](" + String.join(",", fields.keySet().stream().map(k -> "[" + k + "]").toList())
                + ") VALUES(" + String.join(",", Collections.nCopies(fields.size(), "?")) + ")";
        try (var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1; for (Object value : fields.values()) ps.setObject(i++, value);
            require(ps.executeUpdate() == 1);
            try (var rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }
    private void explicitInsert(Connection con, String table, LinkedHashMap<String, Object> fields) throws SQLException {
        boolean identity = scalar(con, "SELECT COUNT_BIG(*) FROM sys.identity_columns WHERE object_id=OBJECT_ID(?)", "dbo." + table) > 0;
        // SET inside JDBC's prepared RPC scope is restored when that RPC returns.
        // Use the top-level session batch for this fixed fixture-only setting.
        try (var statement = con.createStatement()) {
            if (identity) statement.execute("SET IDENTITY_INSERT dbo.[" + table + "] ON");
            try { insert(con, table, fields); }
            finally { if (identity) statement.execute("SET IDENTITY_INSERT dbo.[" + table + "] OFF"); }
        }
    }

    private void runtimePool() {
        var config = new HikariConfig();
        config.setDataSource(source(database, login, password));
        config.setMaximumPoolSize(1); config.setMinimumIdle(1); config.setMaxLifetime(0); config.setIdleTimeout(0);
        config.setConnectionTimeout(10000); config.setPoolName("local-case-verifier");
        pool = new HikariDataSource(config);
        session = new RuntimeSessionService(pool);
        service = adapter(() -> {
            try { return session.getConnection(); }
            catch (SQLException ex) { throw new IllegalStateException("Local connection unavailable"); }
        });
    }
    private static CaseServicePort adapter(DbSessionProvider provider) { return new CaseServiceAdapter(new CaseDao(provider)); }

    private void pooledContext() throws Exception {
        session.initialize(tenantA, actorA);
        try (var con = session.getConnection()) { spid = (int) scalar(con, "SELECT @@SPID"); }
        for (int[] identity : List.of(new int[]{tenantA, actorA2}, new int[]{tenantB, actorB}, new int[]{tenantA, actorA})) {
            session.initialize(identity[0], identity[1]);
            try (var con = session.getConnection()) {
                identity(con, database);
                require(scalar(con, "SELECT @@SPID") == spid);
                require(scalar(con, "SELECT CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))") == identity[0]);
                require(scalar(con, "SELECT CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))") == identity[1]);
                require(scalar(con, "SELECT IS_SRVROLEMEMBER('sysadmin')") == 0);
                require(scalar(con, "SELECT IS_ROLEMEMBER('db_owner')") == 0);
                require(scalar(con, "SELECT CASE WHEN USER_NAME()=? THEN 1 ELSE 0 END", login) == 1);
                require(scalar(con, "SELECT HAS_PERMS_BY_NAME('dbo.Cases','OBJECT','UPDATE')") == 0);
                require(scalar(con, "SELECT HAS_PERMS_BY_NAME('dbo.AuditLog','OBJECT','DELETE')") == 0);
                require(scalar(con, "SELECT HAS_PERMS_BY_NAME('sec','SCHEMA','CONTROL')") == 0);
                require(scalar(con, "SELECT HAS_PERMS_BY_NAME('sec.TenantFilter','OBJECT','ALTER')") == 0);
            }
            require(service.searchMinimizedCases("Verify matched", identity[0], identity[1], 0, 25).items().size() == 25);
        }
        session.clear();
        boolean denied = false;
        try { session.getConnection().close(); } catch (IllegalStateException expected) { denied = true; }
        require(denied); session.initialize(tenantA, actorA);
        var failSecondKey = new java.util.concurrent.atomic.AtomicBoolean(true);
        var closedFailedLease = new java.util.concurrent.atomic.AtomicInteger();
        var failing = (javax.sql.DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{javax.sql.DataSource.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(pool, args);
                        if (!method.getName().equals("getConnection")) return result;
                        Connection real = (Connection) result;
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
                            if (m.getName().equals("prepareStatement") && a[0].toString().contains("PrincipalUserId")
                                    && failSecondKey.compareAndSet(true, false)) throw new SQLException("Local second-key setup injection");
                            if (m.getName().equals("close")) closedFailedLease.incrementAndGet();
                            try { return m.invoke(real, a); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        var brokenSetup = new RuntimeSessionService(failing); brokenSetup.initialize(tenantB, actorB);
        try { brokenSetup.getConnection().close(); throw new AssertionError("Second-key setup failure must withhold the lease"); }
        catch (SQLException expected) { require(closedFailedLease.get() == 1); }
        session.initialize(tenantA, actorA);
        try (var con = session.getConnection()) {
            require(scalar(con, "SELECT @@SPID") == spid);
            require(scalar(con, "SELECT CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))") == tenantA);
            require(scalar(con, "SELECT CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))") == actorA);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.Cases WHERE ShaleClientId<>?", tenantA) == 0);
        }
    }

    private void rlsEnforcement() throws Exception {
        for (int tenant : List.of(tenantA, tenantB)) {
            session.initialize(tenant, tenant == tenantA ? actorA : actorB);
            try (var con = session.getConnection()) {
                for (String table : STRICT) require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.[" + table + "] WHERE ShaleClientId<>? OR ShaleClientId IS NULL", tenant) == 0);
                for (String table : OVERLAY) require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.[" + table + "] WHERE ShaleClientId IS NOT NULL AND ShaleClientId<>?", tenant) == 0);
                for (String table : List.of("Statuses", "PracticeAreas", "CaseDateTypes", "CaseDateTypeSemanticRoleMappings")) {
                    require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.[" + table + "] WHERE ShaleClientId IS NULL") > 0);
                    require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.[" + table + "] WHERE ShaleClientId=?", tenant) > 0);
                }
                context(con, null, null);
                for (String table : STRICT) require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.[" + table + "]") == 0);
            }
        }
        session.initialize(tenantA, actorA);
    }

    private static List<Long> ids(MinimizedCasePage page) { return page.items().stream().map(MinimizedCaseOverview::caseId).toList(); }
    private void selectedReads() {
        for (int tenant : List.of(tenantA, tenantB)) {
            int actor = tenant == tenantA ? actorA : actorB;
            List<Long> expected = tenant == tenantA ? aCases : bCases;
            session.initialize(tenant, actor);
            var first = service.searchMinimizedCases("Verify matched", tenant, actor, 0, 25);
            var second = service.searchMinimizedCases("Verify matched", tenant, actor, 1, 25);
            require(ids(first).equals(expected.subList(0, 25)) && first.hasMore());
            require(ids(second).equals(expected.subList(25, 27)) && !second.hasMore());
            require(service.searchMinimizedCases("Verify matched", tenant, actor, 2, 25).items().isEmpty());
            List<Long> allAssigned = new ArrayList<>();
            for (int page = 0; page <= 1; page++) {
                var assigned = service.listMinimizedAssignedCases(tenant, actor, page, 25);
                require(assigned.hasMore() == (page == 0)); allAssigned.addAll(ids(assigned));
            }
            var expectedAssigned = new HashSet<>(expected);
            if (tenant == tenantA) expectedAssigned.add(closed);
            require(allAssigned.size() == expectedAssigned.size() && new HashSet<>(allAssigned).equals(expectedAssigned));
            require(service.readMinimizedCaseOverview(tenant == tenantA ? bCases.getFirst() : aCases.getFirst(), tenant, actor).isEmpty());
            require(service.readMinimizedCaseOverview(tenant == tenantA ? aDeleted : bDeleted, tenant, actor).isEmpty());
            require(service.readMinimizedCaseOverview(Integer.MAX_VALUE, tenant, actor).isEmpty());
            var overview = service.readMinimizedCaseOverview(expected.getFirst(), tenant, actor).orElseThrow();
            require(overview.responsibleAttorney().userId() == actor);
            require(overview.status().id() == (tenant == tenantA ? statusGlobal : statusB));
            require(overview.practiceArea().id() == (tenant == tenantA ? areaA : areaB));
            if (tenant == tenantA) require(overview.primaryLegalAssistant().userId() == actorA2);
            else require(overview.primaryLegalAssistant() == null);
        }
        session.initialize(tenantA, actorA);
        require(service.readMinimizedCaseOverview(unassigned, tenantA, actorA).isPresent());
        require(service.readMinimizedCaseOverview(closed, tenantA, actorA).orElseThrow().status().id() == statusClosed);
        require(service.searchMinimizedCases("résumé漢字%_[", tenantA, actorA, 0, 25).items().size() == 1);
        require(service.searchMinimizedCases("", tenantA, actorA, 0, 25).items().isEmpty());
    }

    private void actorAuthority() throws Exception {
        long before = auditCount();
        for (int actor : List.of(removedA, inactiveA, actorB)) {
            session.initialize(tenantA, actor);
            denies(service, tenantA, actor);
        }
        session.initialize(tenantA, actorA);
        for (Integer tenant : Arrays.asList(null, tenantB, tenantA)) {
            for (Integer actor : Arrays.asList(null, actorA2)) {
                CaseServicePort raw = adapter(() -> {
                    Connection con = null;
                    try { con = pool.getConnection(); context(con, tenant, actor); return con; }
                    catch (SQLException ex) {
                        if (con != null) try { con.close(); } catch (SQLException ignored) { /* fail closed */ }
                        throw new IllegalStateException("Local connection unavailable");
                    }
                });
                denies(raw, tenantA, actorA);
            }
        }
        require(auditCount() == before);
        session.initialize(tenantA, actorA);
    }

    private void foreignJoinProtection() throws Exception {
        // A single-column relationship may permit foreign lookup identity; a composite FK may
        // reject it. Either protection is valid. Never drop/disable constraints to build a fixture.
        session.initialize(tenantA, actorA);
        long id = unassigned;
        boolean changed = false;
        try (var con = adminConnection(database)) {
            owned(con); context(con, tenantA, actorA);
            try {
                update(con, "UPDATE dbo.Cases SET PracticeAreaId=? WHERE Id=? AND ShaleClientId=?", areaB, id, tenantA);
                changed = true;
            } catch (SQLException ex) { require(ex.getErrorCode() == 547); }
        }
        try {
            var view = service.readMinimizedCaseOverview(id, tenantA, actorA).orElseThrow();
            if (changed) require(view.practiceArea() == null);
            else require(view.practiceArea().id() == areaA);
        } finally {
            if (changed) try (var con = adminConnection(database)) {
                owned(con); context(con, tenantA, actorA);
                update(con, "UPDATE dbo.Cases SET PracticeAreaId=? WHERE Id=? AND ShaleClientId=?", areaA, id, tenantA);
            }
        }
        // The authoritative CaseUsers composite User/Tenant FK must reject foreign membership.
        try (var con = adminConnection(database)) {
            owned(con); context(con, tenantA, actorA);
            con.setAutoCommit(false);
            boolean rejected = false;
            try { assignment(con, tenantA, id, actorB, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY); }
            catch (SQLException ex) { require(ex.getErrorCode() == 547); rejected = true; }
            finally { con.rollback(); }
            require(rejected);
        }
    }
    private void denies(CaseServicePort portService, int tenant, int actor) {
        expect(CaseReadException.Kind.DENIED, () -> portService.searchMinimizedCases("Verify", tenant, actor, 0, 25));
        expect(CaseReadException.Kind.DENIED, () -> portService.searchMinimizedCases("", tenant, actor, 0, 25));
        expect(CaseReadException.Kind.DENIED, () -> portService.listMinimizedAssignedCases(tenant, actor, 0, 25));
        expect(CaseReadException.Kind.DENIED, () -> portService.readMinimizedCaseOverview(aCases.getFirst(), tenant, actor));
    }
    private static void expect(CaseReadException.Kind kind, Runnable operation) {
        boolean withheld = false;
        try { operation.run(); }
        catch (CaseReadException ex) { require(ex.kind() == kind); withheld = true; }
        require(withheld); // A normally returned DTO/Optional/page is always a failure for this scenario.
    }

    private long auditCount() throws SQLException {
        long count = 0;
        try (var con = adminConnection(database)) {
            owned(con);
            for (int tenant : List.of(tenantA, tenantB)) {
                context(con, tenant, null);
                count += scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog WHERE ShaleClientId=?", tenant);
            }
        }
        return count;
    }
    private void overviewAudit() throws Exception {
        session.initialize(tenantA, actorA);
        long before = auditCount();
        service.searchMinimizedCases("Verify", tenantA, actorA, 0, 25);
        service.listMinimizedAssignedCases(tenantA, actorA, 0, 25);
        require(auditCount() == before); // Explicit first-slice summary exemption, no fabricated event.
        long contextBefore = exactAuditCount(tenantA, actorA, aCases.getFirst());
        var result = service.readMinimizedCaseOverview(aCases.getFirst(), tenantA, actorA).orElseThrow();
        require(result.caseId() == aCases.getFirst() && auditCount() == before + 1);
        require(exactAuditCount(tenantA, actorA, aCases.getFirst()) == contextBefore + 1);
        try (var con = adminConnection(database)) {
            owned(con); context(con, tenantA, actorA);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog WHERE ShaleClientId=? AND UserId=? AND ObjectTypeId=1 AND ObjectId=? "
                    + "AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 AND StringValue='action=READ;screen=Case.Overview' "
                    + "AND DateValue IS NULL AND BooleanValue IS NULL AND IntValue IS NULL AND EntryDate IS NOT NULL",
                    tenantA, actorA, aCases.getFirst()) >= 1);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog WHERE FieldName<>'Case.Overview.Read' OR StringValue<>'action=READ;screen=Case.Overview' "
                    + "OR TRY_CONVERT(int,FieldCode)<>4 OR ObjectTypeId<>1 OR DateValue IS NOT NULL OR BooleanValue IS NOT NULL OR IntValue IS NOT NULL") == 0);
        }
        service.readMinimizedCaseOverview(aCases.getFirst(), tenantA, actorA).orElseThrow();
        require(auditCount() == before + 2);
        session.initialize(tenantB, actorB);
        long bBefore;
        try (var con = session.getConnection()) { bBefore = scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog"); }
        service.readMinimizedCaseOverview(bCases.getFirst(), tenantB, actorB).orElseThrow();
        try (var con = session.getConnection()) {
            require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog") == bBefore + 1);
            require(scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog WHERE ShaleClientId<>?", tenantB) == 0);
        }
        session.initialize(tenantA, actorA);
    }

    private long exactAuditCount(int tenant, int actor, long id) throws SQLException {
        try (var con = adminConnection(database)) {
            owned(con); context(con, tenant, actor);
            return scalar(con, "SELECT COUNT_BIG(*) FROM dbo.AuditLog WHERE ShaleClientId=? AND UserId=? AND ObjectId=? "
                    + "AND ObjectTypeId=1 AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 "
                    + "AND StringValue='action=READ;screen=Case.Overview' AND DateValue IS NULL AND BooleanValue IS NULL AND IntValue IS NULL AND EntryDate IS NOT NULL",
                    tenant, actor, id);
        }
    }

    private void auditDenied() throws Exception {
        long before = auditCount();
        try (var con = adminConnection(database)) { owned(con); execute(con, "DENY INSERT ON dbo.AuditLog TO [" + login + "]"); }
        try { expect(CaseReadException.Kind.AUDIT_UNAVAILABLE, () -> service.readMinimizedCaseOverview(aCases.getFirst(), tenantA, actorA)); }
        finally {
            try (var con = adminConnection(database)) {
                owned(con); execute(con, "REVOKE INSERT ON dbo.AuditLog FROM [" + login + "]");
                execute(con, "GRANT INSERT ON dbo.AuditLog TO [" + login + "]");
            }
        }
        require(auditCount() == before);
        service.readMinimizedCaseOverview(aCases.getFirst(), tenantA, actorA).orElseThrow();
        require(auditCount() == before + 1); // Deliberate retry only, after exact harness grant restoration.
    }

    enum Fault { BEFORE_COMMIT, AFTER_COMMIT }
    private void commitFault(Fault fault, int durableDelta) throws Exception {
        long before = auditCount();
        long contextBefore = exactAuditCount(tenantA, actorA, aCases.getFirst());
        int[] commits = {0}, rollbacks = {0};
        CaseServicePort wrapped = adapter(() -> {
            try { return faultConnection(session.getConnection(), fault, commits, rollbacks); }
            catch (SQLException ex) { throw new IllegalStateException("Local connection unavailable"); }
        });
        expect(CaseReadException.Kind.AUDIT_UNAVAILABLE, () -> wrapped.readMinimizedCaseOverview(aCases.getFirst(), tenantA, actorA));
        require(commits[0] == 1 && rollbacks[0] == 1);
        require(auditCount() == before + durableDelta);
        require(exactAuditCount(tenantA, actorA, aCases.getFirst()) == contextBefore + durableDelta);
        // AFTER_COMMIT's real SQL commit is durable despite the production rollback attempt.
        // Never delete/replay that row. Test does not claim it models a real network/driver outage.
    }

    static Connection faultConnection(Connection real, Fault fault, int[] commits, int[] rollbacks) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("commit")) {
                commits[0]++;
                if (fault == Fault.AFTER_COMMIT) real.commit();
                throw new SQLException("Injected test-only commit acknowledgement failure", "08007");
            }
            if (method.getName().equals("rollback")) rollbacks[0]++;
            try { return method.invoke(real, args); }
            catch (InvocationTargetException ex) { throw ex.getCause(); }
        });
    }

    private void cleanup() throws Exception {
        if (pool != null) pool.close();
        if (!createdDatabase) return;
        // Refuse cleanup after any missing/changed marker or identity. Do not infer ownership from name.
        try (var con = adminConnection(database)) { owned(con); }
        try (var con = adminConnection("master")) {
            con.setCatalog(database); owned(con);
            con.setCatalog("master"); identity(con, "master");
            execute(con, "ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            execute(con, "DROP DATABASE [" + database + "]");
            if (createdLogin) execute(con, "DROP LOGIN [" + login + "]");
        }
        pass("cleanup");
    }

    private static void context(Connection con, Integer tenant, Integer actor) throws SQLException {
        update(con, "EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=?", tenant);
        update(con, "EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=?", actor);
    }
    private static void execute(Connection con, String sql) throws SQLException {
        try (var ps = con.prepareStatement(sql)) { ps.setQueryTimeout(10); ps.execute(); }
    }
    private static void update(Connection con, String sql, Object... values) throws SQLException {
        try (var ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(5); int i = 1; for (Object value : values) ps.setObject(i++, value); ps.execute();
        }
    }
    private static long scalar(Connection con, String sql, Object... values) throws SQLException {
        try (var ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(5); int i = 1; for (Object value : values) ps.setObject(i++, value);
            try (var rs = ps.executeQuery()) { require(rs.next()); return rs.getLong(1); }
        }
    }
}
