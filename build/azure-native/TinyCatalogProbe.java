import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

/** Standalone synthetic diagnostic. No Shale runtime, installer, fixture or receipts. */
public final class TinyCatalogProbe {
    static final int SOCKET_MILLIS = 5000, QUERY_SECONDS = 20;
    static final List<String> IDS = List.of("DATABASE", "SECURITY", "PRINCIPALS", "DEPENDENCIES", "COMPONENT_1", "BATCH_19");
    interface Connections { Connection open(boolean owner) throws SQLException; }
    interface Evidence { void write(String line); }
    record Check(String id, String sql, int rows, boolean components) {}

    // Do not inspect messages, causes, suppressed failures or chained SQLExceptions.
    // 08S01 alone means communication failure, not necessarily a socket timeout.
    static String category(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable item = failure;
        for (int depth = 0; item != null && depth < 8 && seen.add(item); depth++) {
            if (item instanceof java.net.SocketTimeoutException) return "SOCKET_TIMEOUT";
            item = item.getCause(); // type only; never serialize the cause or its text
        }
        if (!(failure instanceof SQLException sql)) return "OTHER_SANITIZED";
        if (sql instanceof SQLTimeoutException || Set.of("HY008", "HYT00", "HYT01").contains(Objects.toString(sql.getSQLState(), ""))) return "QUERY_TIMEOUT";
        if (Set.of(229, 230, 262, 297, 300, 916).contains(sql.getErrorCode())) return "PERMISSION";
        if (Set.of(207, 208, 209, 4104, 4121).contains(sql.getErrorCode())) return "BINDING";
        return "OTHER_SANITIZED";
    }
    static String evidence(String id, String outcome, long millis, boolean closed) {
        if (!IDS.contains(id) && !Set.of("SETUP", "LAUNCHER", "CLEANUP").contains(id)) throw new IllegalArgumentException();
        if (!Set.of("PASS", "PERMISSION", "QUERY_TIMEOUT", "SOCKET_TIMEOUT", "BINDING", "OTHER_SANITIZED").contains(outcome) || millis < 0) throw new IllegalArgumentException();
        return id + "\t" + outcome + "\t" + millis + "\tconnectionClosed=" + closed;
    }
    static List<Check> checks(String batch) {
        int end = batch.indexOf("SET @component=", batch.indexOf("SELECT 1 AS ComponentOrdinal"));
        if (end < 0) throw new IllegalArgumentException();
        return List.of(
            new Check("DATABASE", "SELECT COUNT_BIG(*) FROM sys.databases WHERE database_id=DB_ID()", 1, false),
            new Check("SECURITY", "SELECT COUNT_BIG(*) FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id WHERE s.is_enabled=1", 1, false),
            new Check("PRINCIPALS", "SELECT COUNT_BIG(*) FROM sys.database_permissions p JOIN sys.database_principals d ON d.principal_id=p.grantee_principal_id WHERE d.name=N'AiCaseDataCatalogVerifier'", 6, false),
            new Check("DEPENDENCIES", "SELECT COUNT_BIG(*) FROM sys.sql_expression_dependencies WHERE referencing_id=OBJECT_ID(N'dbo.AiDataNormalizeV1')", 1, false),
            new Check("COMPONENT_1", batch.substring(0, end), 1, true),
            new Check("BATCH_19", batch, 19, true));
    }
    static void run(Connections connections, String batch, String password, Evidence output) {
        long start = System.nanoTime();
        try (Connection owner = connections.open(true)) {
            setup(owner, password);
        } catch (Throwable failure) {
            output.write(evidence("SETUP", category(failure), elapsed(start), true));
            return;
        }
        // No owner catalog execution before (or after) these fixed checks. Each new
        // contained-user connection runs exactly one check, even after prior failure.
        for (Check check : checks(batch)) {
            start = System.nanoTime();
            Connection connection = null;
            String outcome = "PASS";
            boolean closed = true;
            try {
                connection = connections.open(false);
                read(connection, check);
            } catch (Throwable failure) {
                outcome = category(failure);
            } finally {
                long millis = elapsed(start);
                if (connection != null) {
                    try { closed = connection.isClosed(); } catch (Throwable ignored) { outcome = "OTHER_SANITIZED"; }
                    try { connection.close(); } catch (Throwable ignored) { outcome = "OTHER_SANITIZED"; }
                }
                output.write(evidence(check.id(), outcome, millis, closed));
            }
        }
    }
    static long elapsed(long start) { return Math.max(0, (System.nanoTime() - start) / 1_000_000); }
    static void read(Connection connection, Check check) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_SECONDS);
            boolean more = statement.execute(check.sql());
            int rows = 0, gaps = 0;
            while (true) {
                if (more) {
                    try (ResultSet result = statement.getResultSet()) {
                        if (!result.next()) throw new IllegalStateException();
                        if (check.components()) {
                            if (result.getInt(1) != ++rows || result.getLong(3) <= 0 || result.wasNull()) throw new IllegalStateException();
                            String hash = result.getString(4);
                            if (hash == null || !hash.matches("[0-9A-Fa-f]{64}")) throw new IllegalStateException();
                        } else {
                            if (result.getLong(1) != check.rows()) throw new IllegalStateException();
                            rows++;
                        }
                        if (result.next()) throw new IllegalStateException();
                        gaps = 0;
                    }
                } else {
                    if (statement.getUpdateCount() != -1) throw new IllegalStateException();
                    if (++gaps > 23) break; // bounded traversal only; never resubmit SQL
                }
                more = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
            }
            if (rows != (check.components() ? check.rows() : 1)) throw new IllegalStateException();
        }
    }
    static void sql(Connection connection, String text) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.setQueryTimeout(20); statement.execute(text); }
    }
    static void setup(Connection c, String password) throws SQLException {
        if (password == null || !password.matches("Aa1![0-9a-f]{64}")) throw new IllegalArgumentException();
        sql(c, "CREATE ROLE AiCaseDataRuntime AUTHORIZATION dbo; CREATE ROLE AiCaseDataCatalogVerifier AUTHORIZATION dbo; CREATE USER catalog_probe WITH PASSWORD='" + password + "'; ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER catalog_probe; CREATE ROLE AiIntegrationRuntime; CREATE ROLE AiIntegrationBootstrap; CREATE ROLE AiCapabilityOperator; CREATE TABLE dbo.AiCaseDataDeployment(Id int PRIMARY KEY); CREATE TABLE dbo.AiCaseDataInvocation(Id int PRIMARY KEY,TenantId int NOT NULL DEFAULT 7,Computed AS Id+1,CHECK(Id>0)); CREATE INDEX IX_probe ON dbo.AiCaseDataInvocation(TenantId);");
        sql(c, "CREATE SCHEMA sec AUTHORIZATION dbo;");
        sql(c, "CREATE FUNCTION sec.fn_FilterByTenant(@TenantId int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS allowed WHERE @TenantId=7;");
        sql(c, "CREATE SECURITY POLICY sec.probe_policy ADD FILTER PREDICATE sec.fn_FilterByTenant(TenantId) ON dbo.AiCaseDataInvocation WITH(STATE=ON);");
        sql(c, "CREATE VIEW dbo.AiDataNormalizeV1 AS SELECT Id FROM dbo.AiCaseDataInvocation;");
        sql(c, "ALTER TABLE dbo.AiCaseDataInvocation ADD CONSTRAINT FK_probe FOREIGN KEY(Id) REFERENCES dbo.AiCaseDataDeployment(Id);");
        sql(c, "CREATE TRIGGER dbo.AiDataImmutableInvocation ON dbo.AiCaseDataInvocation AFTER INSERT AS RETURN;");
        sql(c, "CREATE TABLE dbo.AiDataUnicodeRange(StartCodepoint int NOT NULL PRIMARY KEY,EndCodepoint int NOT NULL,Category int NOT NULL,Whitespace bit NOT NULL,LetterDigit bit NOT NULL,Cased bit NOT NULL);");
        sql(c, "CREATE TABLE dbo.AiDataUnicodeLower(Codepoint int NOT NULL PRIMARY KEY,LowerText nvarchar(4) NOT NULL);");
        sql(c, "CREATE TABLE dbo.AiDataWordTransition(StateId int NOT NULL,Category int NOT NULL,NextState int NOT NULL,IsEnd bit NOT NULL,Lookahead bit NOT NULL,PRIMARY KEY(StateId,Category));");
        sql(c, "INSERT dbo.AiCaseDataDeployment VALUES(1); INSERT dbo.AiCaseDataInvocation(Id,TenantId) VALUES(1,7); INSERT dbo.AiDataUnicodeRange VALUES(65,65,1,0,1,1); INSERT dbo.AiDataUnicodeLower VALUES(65,N'a'); INSERT dbo.AiDataWordTransition VALUES(0,1,0,0,0);");
        sql(c, "GRANT VIEW DEFINITION TO AiCaseDataCatalogVerifier;");
        sql(c, "GRANT SELECT ON OBJECT::sys.sql_expression_dependencies TO AiCaseDataCatalogVerifier;");
        sql(c, "GRANT SELECT ON OBJECT::dbo.AiCaseDataDeployment TO AiCaseDataCatalogVerifier;");
        sql(c, "GRANT SELECT ON OBJECT::dbo.AiDataUnicodeRange TO AiCaseDataCatalogVerifier;");
        sql(c, "GRANT SELECT ON OBJECT::dbo.AiDataUnicodeLower TO AiCaseDataCatalogVerifier;");
        sql(c, "GRANT SELECT ON OBJECT::dbo.AiDataWordTransition TO AiCaseDataCatalogVerifier;");
    }
    static Properties properties(boolean owner, Map<String,String> env) {
        Properties p = new Properties();
        p.setProperty("user", owner ? env.get("TINY_PROBE_OWNER") : "catalog_probe");
        p.setProperty("password", env.get(owner ? "TINY_PROBE_OWNER_PASSWORD" : "TINY_PROBE_PASSWORD"));
        p.setProperty("encrypt", "true"); p.setProperty("trustServerCertificate", "false");
        p.setProperty("socketTimeout", owner ? "120000" : "5000");
        p.setProperty("loginTimeout", "15"); p.setProperty("connectRetryCount", "0");
        return p;
    }
    public static void main(String[] args) {
        // Eliminate JUL driver output before the driver is loaded; never print failures.
        LogManager.getLogManager().reset(); Logger.getLogger("").setLevel(Level.OFF);
        try {
            Map<String,String> env = System.getenv();
            if (args.length != 1 || !"AUTHORIZE_TINY_SYNTHETIC_ONLY".equals(env.get("TINY_PROBE_OPT_IN")) ||
                !Objects.toString(env.get("TINY_PROBE_HOST"), "").matches("[a-z0-9-]+\\.database\\.windows\\.net") ||
                !Objects.toString(env.get("TINY_PROBE_DATABASE"), "").matches("catalogprobe_[0-9a-f]{32}")) throw new IllegalArgumentException();
            String url = "jdbc:sqlserver://" + env.get("TINY_PROBE_HOST") + ":1433;databaseName=" + env.get("TINY_PROBE_DATABASE");
            String batch = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            run(owner -> DriverManager.getConnection(url, properties(owner, env)), batch, env.get("TINY_PROBE_PASSWORD"), System.out::println);
        } catch (Throwable failure) { System.out.println(evidence("LAUNCHER", "OTHER_SANITIZED", 0, true)); }
    }
}
