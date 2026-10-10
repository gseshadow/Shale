package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.semantics.RoleSemantics;
import com.shale.data.dao.CaseDao;

/** Executes the production adapter/gateway/DAO against JDBC doubles, not a live SQL engine. */
class CaseSearchPagingJdbcTest {
    @ParameterizedTest
    @CsvSource({"0,25,0", "1,25,25", "100,100,10000"})
    void productionGatewayExecutesExactPageBindings(int page, int size, int offset) {
        Jdbc jdbc = new Jdbc(List.of(row(501, "Smith", "Legacy narrative")));
        List<CaseOverviewDto> result = jdbc.adapter().searchCasesPage(" Smith ", 41, 31, offset, size);
        assertEquals(List.of(501L), result.stream().map(CaseOverviewDto::getCaseId).toList());
        assertEquals("Legacy narrative", result.getFirst().getDescription(), "R1 must preserve the legacy DTO");
        assertEquals(1, jdbc.connections, "One borrowed connection, no per-case hydration");
        assertEquals(List.of("tenant", "actor", "cases"), jdbc.executions);
        assertEquals(Map.of(1, 31, 2, 41), jdbc.actorBindings, "Actor eligibility must remain tenant-qualified");
        assertEquals(Map.of(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY,
                2, RoleSemantics.ROLE_LEGAL_ASSISTANT, 3, 41, 4, "%smith%", 5, page * size, 6, size),
                jdbc.caseBindings, "SQL must fetch requested size, not (page + 1) * size");
        assertTrue(jdbc.caseSql.contains("ORDER BY c.Name ASC,c.Id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"),
                "Name ties must use Case ID before SQL pagination");
        assertTrue(jdbc.caseSql.contains("WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0"),
                "Tenant and active-case predicates must remain before pagination");
        assertTrue(jdbc.caseSql.contains("LOWER(COALESCE(c.Name,'')) LIKE ?"), "Search remains Case-name only");
    }

    @Test
    void shortFinalAndEmptyPagesAreReturnedWithoutFurtherJavaSlicing() {
        Jdbc finalPage = new Jdbc(List.of(row(503, "Same name", "last")));
        assertEquals(List.of(503L), finalPage.adapter().searchCasesPage("same", 41, 31, 2, 2)
                .stream().map(CaseOverviewDto::getCaseId).toList(), "A final SQL page must not be sliced again");
        Jdbc empty = new Jdbc(List.of());
        assertEquals(List.of(), empty.adapter().searchCasesPage("same", 41, 31, 10000, 100));
        assertEquals(10000, empty.caseBindings.get(5));
        assertEquals(100, empty.caseBindings.get(6));
    }

    @Test
    void tiedNamesRetainSqlRowOrderAndAuthoritativeIds() {
        Jdbc jdbc = new Jdbc(List.of(row(501, "Same name", "first"), row(502, "Same name", "second")));
        var result = jdbc.adapter().searchCasesPage("same", 41, 31, 25, 25);
        assertEquals(List.of(501L, 502L), result.stream().map(CaseOverviewDto::getCaseId).toList(),
                "Mapping must preserve tied-name rows and database order; IDs are identity");
        assertTrue(jdbc.caseSql.contains("ORDER BY c.Name ASC,c.Id ASC"),
                "Generated SQL must request deterministic tied-name ordering (not live collation proof)");
    }

    @Test
    void literalWildcardsUnicodeAndBlankBehaviorArePreserved() {
        Jdbc jdbc = new Jdbc(List.of(row(501, "Élan_%[東京", "")));
        jdbc.adapter().searchCasesPage("  ÉLAN_%[東京  ", 41, 31, 25, 25);
        assertEquals("%élan[_][%][[]東京%", jdbc.caseBindings.get(4),
                "Unicode normalization and SQL Server literal bracket escapes must remain unchanged");
        Jdbc blank = new Jdbc(List.of());
        assertEquals(List.of(), blank.adapter().searchCasesPage(" \t\n", 41, 31, 10000, 100));
        assertEquals(0, blank.connections, "Blank search must remain idle without borrowing a connection");
    }

    @Test
    void missingOrConflictingTenantAndIneligibleActorFailBeforeCaseQuery() {
        for (Integer tenant : new Integer[] {null, 42}) {
            Jdbc jdbc = new Jdbc(List.of());
            jdbc.sessionTenant = tenant;
            assertThrows(IllegalStateException.class, () -> jdbc.adapter().searchCasesPage("x", 41, 31, 25, 25));
            assertEquals(List.of("tenant"), jdbc.executions, "No actor or case query after tenant failure");
        }
        Jdbc jdbc = new Jdbc(List.of());
        jdbc.eligibleActor = false;
        assertThrows(IllegalArgumentException.class, () -> jdbc.adapter().searchCasesPage("x", 41, 31, 25, 25));
        assertEquals(List.of("tenant", "actor"), jdbc.executions, "Removed/foreign actor must not reach case query");
    }

    @Test
    void legacySearchAndAssignedLimitsStillReachTheirEstablishedSqlOrders() {
        Jdbc search = new Jdbc(List.of());
        search.adapter().searchCases("smith", 41, 31, 25);
        assertEquals(0, search.caseBindings.get(5));
        assertEquals(25, search.caseBindings.get(6));
        Jdbc assigned = new Jdbc(List.of());
        assigned.adapter().listAssignedCases(31, 41, 25);
        assertEquals(Map.of(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY, 2, RoleSemantics.ROLE_LEGAL_ASSISTANT,
                3, 41, 4, 31, 5, 0, 6, 25), assigned.caseBindings);
        assertTrue(assigned.caseSql.contains("EXISTS (SELECT 1 FROM dbo.CaseUsers scope"));
        assertTrue(assigned.caseSql.contains("ORDER BY status_row.StatusSortOrder ASC,dates.IntakeDate DESC,c.Id DESC"));
    }

    private static Map<Object, Object> row(long id, String name, String description) {
        return Map.of("Id", id, "ShaleClientId", 41, "Name", name, "Description", description);
    }

    private static final class Jdbc {
        private final List<Map<Object, Object>> rows;
        private Integer sessionTenant = 41;
        private boolean eligibleActor = true;
        private int connections;
        private final List<String> executions = new ArrayList<>();
        private Map<Integer, Object> caseBindings;
        private Map<Integer, Object> actorBindings;
        private String caseSql;

        Jdbc(List<Map<Object, Object>> rows) { this.rows = rows; }

        CaseServiceAdapter adapter() {
            // Public production constructor creates DaoCaseGateway and the real CaseSummaryDao.
            return new CaseServiceAdapter(new CaseDao(() -> {
                connections++;
                return proxy(Connection.class, (p, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> statement((String) args[0]);
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected connection operation: " + method.getName());
                });
            }));
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> bindings = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
                case "setInt", "setString" -> { bindings.put((Integer) args[0], args[1]); yield null; }
                case "close" -> null;
                case "executeQuery" -> {
                    if (sql.contains("SESSION_CONTEXT")) {
                        executions.add("tenant");
                        yield resultSet(List.of(Collections.singletonMap(1, sessionTenant)));
                    }
                    if (sql.startsWith("SELECT 1 FROM dbo.Users")) {
                        executions.add("actor");
                        actorBindings = Map.copyOf(bindings);
                        yield resultSet(eligibleActor ? List.of(Map.of(1, 1)) : List.of());
                    }
                    assertTrue(sql.contains("FROM dbo.Cases c"), "Production paging must reach Case summary SQL");
                    executions.add("cases");
                    caseSql = sql.replaceAll("\\s+", " ");
                    caseBindings = Map.copyOf(bindings);
                    assertEquals(sql.chars().filter(c -> c == '?').count(), bindings.size(),
                            "Every generated SQL placeholder must have exactly one binding");
                    yield resultSet(rows);
                }
                default -> throw new AssertionError("Unexpected statement operation: " + method.getName());
            });
        }
    }

    private static ResultSet resultSet(List<Map<Object, Object>> rows) {
        int[] cursor = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (p, method, args) -> {
            if (method.getName().equals("next")) return ++cursor[0] < rows.size();
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("wasNull")) return wasNull[0];
            Object value = rows.get(cursor[0]).get(args[0]);
            wasNull[0] = value == null;
            return switch (method.getName()) {
                case "getObject", "getDate", "getTimestamp" -> value;
                case "getString" -> value == null ? null : value.toString();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getBoolean" -> value != null && (Boolean) value;
                default -> throw new AssertionError("Unexpected result operation: " + method.getName());
            };
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
