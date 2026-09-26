package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shale.core.runtime.DbSessionProvider;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executes the public desktop DAO methods and inspects the statements sent to JDBC. */
final class CaseDeadlineRuntimeSqlTest {
    @Test void desktopCollectionsExecuteTheOrdinaryDeadlineFamilyProjection() {
        Harness harness = new Harness();
        CaseSummaryDao dao = new CaseSummaryDao(harness);

        dao.searchActiveByName(7, "case");
        dao.searchDeletedByName(7, "case");
        dao.listActiveRelatedToContact(7, 11);
        dao.listActiveRelatedToOrganization(7, 12);
        dao.listActiveAssignedBoard(7, 21);
        dao.listActiveAssignedForUserDetail(7, 21, 25);
        dao.findActiveGridPage(7, 0, 25, CaseSummaryDao.GridOrder.STATUTE_SOONEST, "",
                CaseSummaryDao.GridStatusMode.UNRESTRICTED, Set.of(), 1L);

        List<String> projections = harness.executed.stream()
                .filter(sql -> sql.contains("family_date.StartsAt"))
                .toList();
        assertEquals(7, projections.size(), "Every requested desktop collection must execute its deadline projection");
        for (String sql : projections) assertRuntimeProjection(sql);
    }

    private static void assertRuntimeProjection(String sql) {
        assertAll(
                () -> assertTrue(sql.contains("family_type.SystemKey)))='statute_of_limitations'")),
                () -> assertTrue(sql.contains("family_type.SystemKey)))='tort_notice_deadline'")),
                () -> assertTrue(sql.contains("candidate.IsDeleted=0"), "Deleted overlays must reset to global"),
                () -> assertTrue(sql.contains("CASE WHEN candidate.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END"),
                        "A nondeleted tenant overlay must mask global"),
                () -> assertTrue(sql.contains("effective_type.IsActive=1"),
                        "Missing or inactive effective types must produce null"),
                () -> assertTrue(sql.contains("ORDER BY family_date.StartsAt ASC,family_date.Id ASC"),
                        "Multiple occurrences must have one deterministic winner"),
                () -> assertTrue(sql.contains("family_date.ShaleClientId=c.ShaleClientId"),
                        "Occurrence reads must remain tenant scoped"),
                () -> assertFalse(sql.contains("CaseDatePresentationSelections")),
                () -> assertFalse(sql.contains("CASE_CARD")),
                () -> assertFalse(sql.contains("SemanticRoleKey='STATUTE_OF_LIMITATIONS'")),
                () -> assertFalse(sql.contains("SemanticRoleKey='TORT_NOTICE_DEADLINE'")));
    }

    private static final class Harness implements DbSessionProvider {
        private final List<String> executed = new ArrayList<>();

        @Override public Connection requireConnection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "prepareStatement" -> statement((String) args[0]);
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement statement(String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setInt", "setLong", "setString", "setBoolean", "setDate", "setNull" -> null;
                        case "executeQuery" -> {
                            executed.add(sql);
                            if (sql.contains("SESSION_CONTEXT")) yield rows(true, 7);
                            if (sql.contains("FROM dbo.Users u WHERE u.id=?")) yield rows(true, 1);
                            yield rows(false, null);
                        }
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static ResultSet rows(boolean one, Integer value) {
        int[] index = {-1};
        return (ResultSet) Proxy.newProxyInstance(CaseDeadlineRuntimeSqlTest.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++index[0] == 0 && one;
                    case "getObject" -> value;
                    case "getInt" -> value == null ? 0 : value;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
