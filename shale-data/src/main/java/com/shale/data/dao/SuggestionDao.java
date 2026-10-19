package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.util.PerformanceLogging;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A single runtime connection and one bounded batch; no cards, dates, directory points or child loads. */
public final class SuggestionDao {
    private static final Logger LOG = LoggerFactory.getLogger(SuggestionDao.class);
    public enum Category { CASE, CONTACT, ORGANIZATION, USER, TASK, CALENDAR_EVENT, DELETED_CASE }
    public record Row(Category category, long id, String name, String detail, int rank) { }
    public record Result(List<Row> rows, boolean failed) {
        public Result { rows = List.copyOf(rows); }
    }
    private final DbSessionProvider db;

    public SuggestionDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db); }

    public Result search(int tenant, String query, boolean deleted, int perCategory, int total,
            Category only, Long id, BooleanSupplier current, Consumer<Runnable> cancellation) {
        if (tenant <= 0) throw new IllegalArgumentException("Tenant must be positive");
        if (perCategory < 1 || perCategory > 5 || total < 1 || total > 35)
            throw new IllegalArgumentException("Invalid suggestion bounds");
        if (id != null && (id <= 0 || only == null)) throw new IllegalArgumentException("Invalid selection");
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank() || text.length() > 2048 || !current.getAsBoolean()) return new Result(List.of(), false);
        String literal = text.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
        // SQL Server LIKE patterns are limited to 8,000 bytes; never silently truncate escaped input.
        if (literal.length() > 3998) return new Result(List.of(), false);
        long start = PerformanceLogging.start();
        long overall = start;
        try (Connection con = Objects.requireNonNull(db.requireConnection(), "Runtime connection unavailable")) {
            SearchPerformance.log(LOG, "connection_and_session_context", 0, "all", 0, PerformanceLogging.elapsedMs(start));
            if (!current.getAsBoolean()) return new Result(List.of(), false);
            // One metadata/tenant check, including the same optional Users visibility columns as UserDao.
            String userFilters;
            start = PerformanceLogging.start();
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId')) AS TenantId,
                        COL_LENGTH(N'dbo.Users', N'IsRemoved') AS RemovedColumn,
                        COL_LENGTH(N'dbo.Users', N'IsActive') AS ActiveColumn,
                        COL_LENGTH(N'dbo.Users', N'IsDeleted') AS DeletedColumn,
                        COL_LENGTH(N'dbo.Users', N'is_deleted') AS LegacyDeletedColumn
                    """)) {
                ps.setQueryTimeout(5);
                cancellation.accept(() -> cancel(ps));
                if (!current.getAsBoolean()) return new Result(List.of(), false);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("TenantId") == null || rs.getInt("TenantId") != tenant)
                        throw new IllegalStateException("Runtime tenant context mismatch");
                    userFilters = userFilters(rs);
                } finally { cancellation.accept(null); }
            }
            SearchPerformance.log(LOG, "tenant_verification", 0, "all", 0, PerformanceLogging.elapsedMs(start));
            if (!current.getAsBoolean()) return new Result(List.of(), false);
            try (PreparedStatement ps = con.prepareStatement(batchSql(userFilters))) {
                ps.setInt(1, tenant);
                // Keep Unicode even when sendStringParametersAsUnicode is disabled on the connection.
                ps.setNString(2, text);
                ps.setNString(3, literal + "%");
                ps.setNString(4, "%" + literal + "%");
                ps.setInt(5, perCategory);
                ps.setInt(6, total);
                ps.setBoolean(7, deleted);
                ps.setInt(8, only == null ? -1 : only.ordinal());
                if (id == null) ps.setNull(9, java.sql.Types.BIGINT); else ps.setLong(9, id);
                ps.setQueryTimeout(5); // Whole batch bound, rather than five seconds for every category.
                cancellation.accept(() -> cancel(ps));
                if (!current.getAsBoolean()) return new Result(List.of(), false);
                start = PerformanceLogging.start();
                boolean resultSet = ps.execute();
                SearchPerformance.log(LOG, "sql_execution_and_first_response", 0, "all", 0,
                        PerformanceLogging.elapsedMs(start));
                var rows = new ArrayList<Row>();
                boolean failed = false;
                long mappingNanos = 0;
                // SQL Server may emit update counts; consume them without assuming driver settings.
                while (resultSet || ps.getUpdateCount() != -1) {
                    if (resultSet) {
                        try (ResultSet rs = ps.getResultSet()) {
                            boolean timings = rs.getMetaData().getColumnLabel(1).equalsIgnoreCase("Provider");
                            while (rs.next()) {
                                long mappingStart = System.nanoTime();
                                if (timings) {
                                    Category provider = Category.values()[rs.getInt("Provider")];
                                    int error = rs.getInt("ErrorCode");
                                    failed |= error != 0;
                                    SearchPerformance.log(LOG, "sql_category", 0, provider.name(), rs.getInt("ResultCount"),
                                            rs.getLong("ElapsedMs"));
                                    if (error != 0) LOG.error("Search suggestions failed provider={} sqlErrorCode={}", provider, error);
                                } else {
                                    rows.add(new Row(Category.values()[rs.getInt("Category")], rs.getLong("Id"),
                                            rs.getString("Name"), rs.getString("Detail"), rs.getInt("MatchRank")));
                                }
                                mappingNanos += System.nanoTime() - mappingStart;
                            }
                        }
                    }
                    resultSet = ps.getMoreResults();
                }
                SearchPerformance.log(LOG, "result_mapping", 0, "all", rows.size(), mappingNanos / 1_000_000);
                SearchPerformance.log(LOG, "sql_execution_and_transfer", 0, "all", rows.size(),
                        PerformanceLogging.elapsedMs(start));
                return new Result(rows, failed);
            } finally { cancellation.accept(null); }
        } catch (SQLException ex) {
            if (!current.getAsBoolean()) return new Result(List.of(), false);
            // SQL exceptions can embed submitted values. Keep only safe diagnostic classification.
            LOG.error("Search suggestions failed rows=0 elapsedMs={} failureClass={} sqlState={} sqlErrorCode={}",
                    PerformanceLogging.elapsedMs(overall), ex.getClass().getSimpleName(), ex.getSQLState(), ex.getErrorCode());
            throw new IllegalStateException("Suggestion query failed", ex);
        } catch (RuntimeException ex) {
            LOG.error("Search suggestions failed rows=0 elapsedMs={} failureClass={}",
                    PerformanceLogging.elapsedMs(overall), ex.getClass().getSimpleName());
            throw ex;
        } finally { cancellation.accept(null); }
    }

    private static void cancel(PreparedStatement ps) {
        try { ps.cancel(); }
        catch (SQLException ex) { LOG.debug("Search cancellation unavailable failureClass={}", ex.getClass().getSimpleName()); }
    }

    private static String userFilters(ResultSet rs) throws SQLException {
        StringBuilder sql = new StringBuilder();
        if (rs.getObject("RemovedColumn") != null) sql.append(" AND ISNULL(u.IsRemoved,0)=0");
        if (rs.getObject("ActiveColumn") != null) sql.append(" AND ISNULL(u.IsActive,1)=1");
        if (rs.getObject("DeletedColumn") != null) sql.append(" AND ISNULL(u.IsDeleted,0)=0");
        if (rs.getObject("LegacyDeletedColumn") != null) sql.append(" AND ISNULL(u.is_deleted,0)=0");
        return sql.toString();
    }

    static String batchSql(String userFilters) {
        String sql = """
                SET NOCOUNT ON;
                DECLARE @tenant int=?, @text nvarchar(2048)=?, @prefix nvarchar(4000)=?, @contains nvarchar(4000)=?,
                    @limit int=?, @total int=?, @deleted bit=?, @only int=?, @id bigint=?;
                IF ISNULL(TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),-1)<>@tenant
                    THROW 51000, 'Runtime tenant context mismatch', 1;
                DECLARE @rows TABLE(Category int,Id bigint,Name nvarchar(max),Detail nvarchar(max),MatchRank int);
                DECLARE @timings TABLE(Provider int,ElapsedMs bigint,ResultCount int,ErrorCode int);
                DECLARE @started datetime2(7), @count int, @error int;
                """;
        String caseFields = "LOWER(LTRIM(RTRIM(COALESCE(c.Name,'')))) Name,LOWER(LTRIM(RTRIM(COALESCE(c.CaseNumber,'')))) Code,"
                + "LOWER(LTRIM(RTRIM(COALESCE(c.OfficePrinterCode,'')))) PrinterCode,CONVERT(nvarchar(20),c.Id) IdentityText";
        List<String> caseMatches = List.of("Name", "Code", "PrinterCode", "IdentityText");
        sql += categorySql(Category.CASE, "dbo.Cases c", "c.Id", "c.Name",
                "CONCAT(COALESCE(c.CaseNumber,''),CASE WHEN NULLIF(c.OfficePrinterCode,'') IS NULL THEN '' ELSE CONCAT(' · ',c.OfficePrinterCode) END)",
                "ISNULL(c.IsDeleted,0)=0", caseFields, caseMatches);
        String contactName = "COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(NULLIF(LTRIM(RTRIM(c.FirstName)),''),' ',NULLIF(LTRIM(RTRIM(c.LastName)),'')))),''),c.Name,'')";
        sql += categorySql(Category.CONTACT, "dbo.Contacts c", "c.Id", contactName, "''",
                "ISNULL(c.IsDeleted,0)=0 AND NULLIF(" + contactName + ",'') IS NOT NULL",
                "LOWER(" + contactName + ") Name,LOWER(COALESCE(c.Name,'')) StoredName,"
                        + "LOWER(COALESCE(c.FirstName,'')) FirstName,LOWER(COALESCE(c.LastName,'')) LastName",
                List.of("Name", "StoredName", "FirstName", "LastName"));
        sql += categorySql(Category.ORGANIZATION, "dbo.Organizations o", "o.Id", "o.Name", "''",
                "ISNULL(o.IsDeleted,0)=0", "LOWER(LTRIM(RTRIM(COALESCE(o.Name,'')))) Name", List.of("Name"));
        String userName = "LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last)))";
        sql += categorySql(Category.USER, "dbo.Users u", "u.id", userName, "''",
                "NULLIF(" + userName + ",'') IS NOT NULL" + userFilters,
                "LOWER(" + userName + ") Name,LOWER(COALESCE(u.name_first,'')) FirstName,LOWER(COALESCE(u.name_last,'')) LastName",
                List.of("Name", "FirstName", "LastName"));
        // Preserve existing Task parent availability, including its existing Case deletion semantics.
        sql += categorySql(Category.TASK, "dbo.Tasks t", "t.Id", "t.Title", "''",
                "ISNULL(t.IsDeleted,0)=0 AND EXISTS(SELECT 1 FROM dbo.Cases c WHERE c.Id=t.CaseId AND c.ShaleClientId=t.ShaleClientId)",
                "LOWER(LTRIM(RTRIM(COALESCE(t.Title,'')))) Name", List.of("Name"));
        sql += categorySql(Category.CALENDAR_EVENT, "dbo.CalendarEvents e", "e.CalendarEventId", "e.Title", "''",
                "ISNULL(e.IsCancelled,0)=0", "LOWER(LTRIM(RTRIM(COALESCE(e.Title,'')))) Name", List.of("Name"));
        sql += categorySql(Category.DELETED_CASE, "dbo.Cases c", "c.Id", "c.Name", "CONCAT('Deleted · ',COALESCE(c.CaseNumber,''))",
                "c.IsDeleted=1 AND EXISTS(SELECT 1 FROM dbo.Users actor WHERE actor.ShaleClientId=@tenant"
                        + " AND actor.id=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')) AND actor.is_admin=1)",
                caseFields, caseMatches);
        return sql + """
                SELECT TOP (@total) Category,Id,Name,Detail,MatchRank FROM @rows
                    ORDER BY Category,MatchRank,LOWER(COALESCE(Name,'')),Id;
                SELECT Provider,ElapsedMs,ResultCount,ErrorCode FROM @timings ORDER BY Provider;
                """;
    }

    private static String categorySql(Category category, String from, String id, String name, String detail,
            String lifecycle, String fields, List<String> matches) {
        String alias = id.substring(0, id.indexOf('.'));
        String exact = matches.stream().map(f -> "f." + f + "=@text").collect(java.util.stream.Collectors.joining(" OR "));
        String prefix = matches.stream().map(f -> "f." + f + " LIKE @prefix").collect(java.util.stream.Collectors.joining(" OR "));
        String contains = matches.stream().map(f -> "f." + f + " LIKE @contains").collect(java.util.stream.Collectors.joining(" OR "));
        int key = category.ordinal();
        return """
                IF (@only=-1 OR @only=%d) %s AND (SELECT COUNT(*) FROM @rows)<@total
                BEGIN
                    SET @started=SYSUTCDATETIME(); SET @count=0; SET @error=0;
                    BEGIN TRY
                        INSERT INTO @rows(Category,Id,Name,Detail,MatchRank)
                        SELECT TOP (@limit) %d,%s,%s,%s,
                            CASE WHEN (%s) THEN 0 WHEN (%s) THEN 1 ELSE 2 END
                        FROM %s CROSS APPLY(SELECT %s) f
                        WHERE %s.ShaleClientId=@tenant AND (%s) AND (@id IS NULL OR %s=@id) AND (%s)
                        ORDER BY CASE WHEN (%s) THEN 0 WHEN (%s) THEN 1 ELSE 2 END,
                            LOWER(COALESCE(%s,'')),%s;
                        SET @count=@@ROWCOUNT;
                    END TRY
                    BEGIN CATCH
                        SET @error=ERROR_NUMBER();
                    END CATCH;
                    INSERT INTO @timings VALUES(%d,DATEDIFF_BIG(millisecond,@started,SYSUTCDATETIME()),@count,@error);
                END;
                """.formatted(key, category == Category.DELETED_CASE ? "AND @deleted=1" : "",
                        key, id, name, detail, exact, prefix, from, fields, alias, lifecycle, id, contains,
                        exact, prefix, name, id, key);
    }
}
