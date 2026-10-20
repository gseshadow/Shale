package com.shale.data.service.adapter;

import com.shale.core.dto.*;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.CaseDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Executes production port/adapter/gateway/DAO SQL and bindings; does not claim SQL Server/RLS execution. */
class CaseUpdatesPagingJdbcTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final byte[] VERSION = {0,0,0,0,0,0,0,1};

    @ParameterizedTest @EnumSource(CaseUpdateQuery.Sort.class)
    void everySortUsesSqlSeekWithTimestampAndIdAndOneLookahead(CaseUpdateQuery.Sort sort) {
        Jdbc jdbc = new Jdbc();
        jdbc.rows = List.of(row(90), row(89), row(88));
        var query = new CaseUpdateQuery(501, 41, 31, "", sort, null, null, null,
                CaseUpdateQuery.Visibility.ACTIVE, new CaseUpdateQuery.Cursor(AT, 100), 2);
        CaseUpdatePage page = jdbc.adapter().searchCaseUpdates(query);
        assertEquals(List.of(90L, 89L), page.items().stream().map(CaseUpdateDto::getId).toList(), "Mapping must preserve SQL row order");
        assertEquals(new CaseUpdateQuery.Cursor(AT, 89), page.next(), "Seek must follow the last displayed row, never the lookahead");
        assertEquals(1, jdbc.connections, "Paging must not hydrate notes/people with additional connections");
        String direction = sort == CaseUpdateQuery.Sort.OLDEST ? "ASC" : "DESC";
        assertTrue(jdbc.sql.endsWith(", caseUpdate.Id " + direction), "Tied timestamps require deterministic IDs");
        assertTrue(jdbc.sql.contains(sort == CaseUpdateQuery.Sort.OLDEST ? "caseUpdate.Id > ?" : "caseUpdate.Id < ?"));
        assertEquals(Map.of(1,3,2,501L,3,41,4,41,5,0,6,Timestamp.valueOf(AT),7,Timestamp.valueOf(AT),8,100L), jdbc.bindings);
        assertTrue(jdbc.sql.contains("TOP (?)"));
        assertTrue(jdbc.sql.contains("caseUpdate.CaseId = ? AND caseUpdate.ShaleClientId = ? AND c.ShaleClientId = ?"));
        assertTrue(jdbc.sql.contains("c.ShaleClientId = caseUpdate.ShaleClientId"));
        assertTrue(jdbc.sql.contains("u.ShaleClientId = caseUpdate.ShaleClientId"));
        if (sort == CaseUpdateQuery.Sort.EDITED) assertTrue(jdbc.sql.contains("COALESCE(caseUpdate.UpdatedAt, caseUpdate.CreatedAt"));
    }

    @Test void combinationsKeepLiteralUnicodeSearchInclusiveDatesAuthorAndLifecycleInSql() {
        for (var visibility : CaseUpdateQuery.Visibility.values()) {
            for (int mask = 0; mask < 8; mask++) {
                Jdbc jdbc = new Jdbc();
                String text = (mask & 1) != 0 ? " ÉLAN_%[東京 " : "";
                LocalDate from = (mask & 2) != 0 ? AT.toLocalDate() : null;
                Integer author = (mask & 4) != 0 ? 17 : null;
                jdbc.adapter().searchCaseUpdates(new CaseUpdateQuery(501,41,31,text,null,from,
                        from,author,visibility,null,50));
                List<Object> expected = new ArrayList<>(List.of(51,501L,41,41));
                if (visibility != CaseUpdateQuery.Visibility.ALL) expected.add(visibility == CaseUpdateQuery.Visibility.ACTIVE ? 0 : 1);
                if (!text.isBlank()) expected.add("%élan[_][%][[]東京%");
                if (from != null) { expected.add(Timestamp.valueOf(from.atStartOfDay())); expected.add(Timestamp.valueOf(from.plusDays(1).atStartOfDay())); }
                if (author != null) expected.add(author);
                assertEquals(expected, new ArrayList<>(jdbc.bindings.values()), "Every filter combination must bind exactly its own values");
                assertEquals(visibility != CaseUpdateQuery.Visibility.ACTIVE, jdbc.adminChecked);
                assertEquals(from != null, jdbc.sql.contains("caseUpdate.CreatedAt < ?"), "Through date is inclusive using next midnight");
                assertEquals(author != null, jdbc.sql.contains("caseUpdate.CreatedByUserId = ?"));
                assertFalse(jdbc.sql.contains("ÉLAN"), "Search text must never be interpolated into SQL");
            }
        }
    }

    @Test void dateRangeMatchesDisplayedLocalDaysAcrossDaylightSavingChange() {
        Jdbc jdbc = new Jdbc();
        var day = LocalDate.of(2026,3,8);
        jdbc.adapter().searchCaseUpdates(new CaseUpdateQuery(501,41,31,"",null,day,day,null,null,null,50)
                .inZone(java.time.ZoneId.of("America/Denver")));
        assertEquals(Timestamp.valueOf("2026-03-08 07:00:00"), jdbc.bindings.get(6));
        assertEquals(Timestamp.valueOf("2026-03-09 06:00:00"), jdbc.bindings.get(7),
                "Inclusive local days can span 23 hours; filtering must agree with displayed local timestamps");
    }

    @Test void emptyAndFinalPagesDoNotOfferAnotherPageAndVersionsAreDefensive() {
        Jdbc jdbc = new Jdbc();
        assertNull(jdbc.adapter().searchCaseUpdates(CaseUpdateQuery.newest(501,41,31,"",50)).next());
        jdbc.rows = List.of(row(91));
        CaseUpdateDto update = jdbc.adapter().searchCaseUpdates(CaseUpdateQuery.newest(501,41,31,"",50)).items().getFirst();
        assertArrayEquals(VERSION, update.rowVersion());
        update.rowVersion()[7] = 50;
        assertArrayEquals(VERSION, update.rowVersion(), "UI must not mutate an authoritative concurrency token");
    }

    @Test void tenantActorCaseAndDeletedAuthorizationRejectBeforeNotesAreRead() {
        for (int tenant : new int[]{0,42}) {
            Jdbc jdbc = new Jdbc(); jdbc.sessionTenant = tenant;
            assertThrows(RuntimeException.class, () -> jdbc.adapter().searchCaseUpdates(CaseUpdateQuery.newest(501,41,31,"",50)));
            assertNull(jdbc.sql);
        }
        Jdbc actor = new Jdbc(); actor.actorExists = false;
        assertThrows(IllegalArgumentException.class, () -> actor.adapter().searchCaseUpdates(CaseUpdateQuery.newest(501,41,31,"",50)));
        assertNull(actor.sql);
        Jdbc missingCase = new Jdbc(); missingCase.caseExists = false;
        assertThrows(IllegalArgumentException.class, () -> missingCase.adapter().searchCaseUpdates(CaseUpdateQuery.newest(502,41,31,"",50)));
        assertEquals(List.of(502L,41), new ArrayList<>(missingCase.caseBindings.values()));
        assertNull(missingCase.sql, "A missing/foreign case must not expose any history");
        for (var visibility : List.of(CaseUpdateQuery.Visibility.ALL, CaseUpdateQuery.Visibility.DELETED)) {
            Jdbc ordinary = new Jdbc(); ordinary.admin = false;
            assertThrows(IllegalArgumentException.class, () -> ordinary.adapter().searchCaseUpdates(
                    new CaseUpdateQuery(501,41,31,"",null,null,null,null,visibility,null,50)));
            assertNull(ordinary.sql, "Crafted deleted filters must fail at DAO authorization, not just UI visibility");
        }
    }

    @Test void authorPickerDelegatesToDistinctMetadataInsteadOfLoadingHistory() {
        Jdbc jdbc = new Jdbc(); jdbc.rows = List.of(row(90));
        var authors = jdbc.adapter().listCaseUpdateAuthors(501,41,31);
        assertEquals(17, authors.getFirst().id());
        assertTrue(jdbc.sql.contains("SELECT DISTINCT caseUpdate.CreatedByUserId"));
        assertFalse(jdbc.sql.contains("NoteText"), "Picking an author must not fetch any note history");
        assertTrue(jdbc.sql.contains("actor.is_admin = 1"), "Deleted-note-only authors must not leak to ordinary readers");
        assertEquals(Map.of(1,501L,2,41,3,31,4,41), jdbc.bindings);
    }

    @Test void versionedEditUsesCreatorCaseTenantActiveAndVersionAndCommitsPhiAuditTogether() {
        Jdbc jdbc = new Jdbc();
        var command = new CaseServicePort.UpdateCaseNoteCommand(90,501,41,31," changed ",VERSION);
        assertTrue(jdbc.adapter().updateCaseNote(command));
        assertEquals(List.of("begin","edit","touch","audit","commit","reset"), jdbc.transaction);
        assertTrue(jdbc.editSql.contains("CreatedByUserId = ? AND ISNULL(IsDeleted, 0) = 0 AND RowVersion = ?"));
        assertEquals("changed", jdbc.editBindings.get(1));
        assertEquals(31, jdbc.editBindings.get(2));
        assertEquals(90L, jdbc.editBindings.get(3));
        assertEquals(501L, jdbc.editBindings.get(4));
        assertEquals(41, jdbc.editBindings.get(5));
        assertEquals(31, jdbc.editBindings.get(6));
        assertArrayEquals(VERSION, (byte[]) jdbc.editBindings.get(7));
        assertEquals(41, jdbc.auditBindings.get(1));
        assertEquals(31, jdbc.auditBindings.get(2));
        assertEquals(3, jdbc.auditBindings.get(3));
        assertEquals(90L, jdbc.auditBindings.get(4));
        assertEquals("CaseUpdates.NoteText", jdbc.auditBindings.get(5));
    }

    @Test void conflictingDeletedOrForeignEditCannotWriteAndAuditFailureRollsBack() {
        Jdbc conflict = new Jdbc(); conflict.editable = false;
        assertFalse(conflict.adapter().updateCaseNote(new CaseServicePort.UpdateCaseNoteCommand(90,501,41,31,"changed",VERSION)));
        assertEquals(List.of("begin","rollback","reset"), conflict.transaction);
        Jdbc failedAudit = new Jdbc(); failedAudit.auditFailure = true;
        assertThrows(RuntimeException.class, () -> failedAudit.adapter().updateCaseNote(
                new CaseServicePort.UpdateCaseNoteCommand(90,501,41,31,"changed",VERSION)));
        assertEquals(List.of("begin","edit","touch","audit","rollback","reset"), failedAudit.transaction);
    }

    @Test void createUsesSameConnectionForNoteCaseTouchAndRequiredPhiAudit() {
        Jdbc jdbc = new Jdbc();
        jdbc.adapter().addCaseNote(new CaseServicePort.AddCaseNoteCommand(501,41,31,"created"));
        assertEquals(List.of("begin","insert","touch","audit","commit","reset"), jdbc.transaction);
        assertEquals(1, jdbc.connections);
        Jdbc failed = new Jdbc(); failed.auditFailure = true;
        assertThrows(RuntimeException.class, () -> failed.adapter().addCaseNote(new CaseServicePort.AddCaseNoteCommand(501,41,31,"created")));
        assertFalse(failed.transaction.contains("commit")); assertTrue(failed.transaction.contains("rollback"));
    }

    private static Map<Object,Object> row(long id) {
        Map<Object,Object> row = new HashMap<>();
        row.put("Id",id); row.put("CaseId",501L); row.put("NoteText","old"); row.put("CreatedByUserId",17);
        row.put("CreatedByDisplayName","Author"); row.put("CreatedAt",Timestamp.valueOf(AT)); row.put("UpdatedAt",Timestamp.valueOf(AT));
        row.put("SortAt",Timestamp.valueOf(AT)); row.put("RowVersion",VERSION.clone()); row.put("IsDeleted",false);
        return row;
    }
    private static final class Jdbc {
        List<Map<Object,Object>> rows = List.of();
        int sessionTenant = 41, connections;
        boolean actorExists = true, caseExists = true, admin = true, adminChecked, editable = true, auditFailure;
        String sql, editSql;
        Map<Integer,Object> bindings, caseBindings, editBindings, auditBindings;
        List<String> transaction = new ArrayList<>();
        CaseServiceAdapter adapter() {
            return new CaseServiceAdapter(new CaseDao(() -> {
                connections++;
                return proxy(Connection.class, (p,m,a) -> switch(m.getName()) {
                    case "prepareStatement" -> statement((String)a[0]);
                    case "setAutoCommit" -> { transaction.add((boolean)a[0] ? "reset" : "begin"); yield null; }
                    case "commit", "rollback" -> { transaction.add(m.getName()); yield null; }
                    case "getMetaData" -> proxy(DatabaseMetaData.class, (p2,m2,a2) -> m2.getName().equals("getColumns") ? resultSet(List.of(Map.of("DATA_TYPE",Types.INTEGER))) : null);
                    case "getCatalog", "close" -> null;
                    default -> throw new AssertionError("Unexpected Connection: " + m.getName());
                });
            }));
        }
        PreparedStatement statement(String source) {
            Map<Integer,Object> values = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (p,m,a) -> {
                String name = m.getName();
                if (name.startsWith("set")) { values.put((Integer)a[0], name.equals("setNull") ? null : a[1]); return null; }
                if (name.equals("close")) return null;
                if (name.equals("executeUpdate")) {
                    if (source.contains("UPDATE dbo.CaseUpdates")) { transaction.add("edit"); editSql=source; editBindings=values; }
                    else if (source.contains("UPDATE dbo.Cases")) transaction.add("touch");
                    else if (source.contains("INSERT INTO dbo.AuditLog")) {
                        transaction.add("audit"); auditBindings=values;
                        if (auditFailure) throw new SQLException("Simulated audit outage");
                    } else fail("Unexpected mutation SQL");
                    return 1;
                }
                assertEquals("executeQuery",name);
                assertEquals(source.chars().filter(c -> c == '?').count(), values.size(), "All placeholders must be bound exactly once");
                if (source.contains("SESSION_CONTEXT")) return resultSet(List.of(Map.of(1,sessionTenant)));
                if (source.contains("INFORMATION_SCHEMA")) return resultSet(List.of(Map.of(1,1)));
                if (source.startsWith("SELECT 1 FROM dbo.Users")) {
                    adminChecked = source.contains("is_admin = 1");
                    return resultSet(actorExists && (!adminChecked || admin) ? List.of(Map.of(1,1)) : List.of());
                }
                if (source.startsWith("SELECT 1 FROM dbo.Cases")) { caseBindings=values; return resultSet(caseExists ? List.of(Map.of(1,1)) : List.of()); }
                if (source.contains("WITH (UPDLOCK, HOLDLOCK)")) return resultSet(editable ? List.of(row(90)) : List.of());
                if (source.contains("INSERT INTO dbo.CaseUpdates")) { transaction.add("insert"); return resultSet(List.of(Map.of(1,90L))); }
                sql=source.replaceAll("\\s+"," ").trim(); bindings=values;
                return resultSet(rows);
            });
        }
    }
    private static ResultSet resultSet(List<Map<Object,Object>> rows) {
        int[] index={-1}; boolean[] wasNull={false};
        return proxy(ResultSet.class, (p,m,a) -> {
            if (m.getName().equals("next")) return ++index[0] < rows.size();
            if (m.getName().equals("close")) return null;
            if (m.getName().equals("wasNull")) return wasNull[0];
            Object v=rows.get(index[0]).get(a[0]); wasNull[0]=v==null;
            return switch(m.getName()) {
                case "getObject", "getTimestamp", "getBytes" -> v;
                case "getString" -> v==null ? null : v.toString();
                case "getInt" -> v==null ? 0 : ((Number)v).intValue();
                case "getLong" -> v==null ? 0L : ((Number)v).longValue();
                case "getBoolean" -> v != null && (Boolean)v;
                default -> throw new AssertionError("Unexpected ResultSet: " + m.getName());
            };
        });
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},handler);
    }
}
