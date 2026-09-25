package com.shale.data.dao;

import static com.shale.core.service.CaseServicePort.SetFieldConfirmationPolicyCommand;
import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.dto.FieldConfirmationPolicyDto;
import com.shale.core.runtime.DbSessionProvider;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** JDBC transaction regression harness that enforces SQL Server's enabled-trigger OUTPUT rule. */
final class FieldConfirmationDaoTriggerIntegrationTest {
    private static final byte[] EMPTY = null;

    @Test void enabledTriggerAllowsEnableAndDisableWhileKeepingImmutableRevisions() {
        TriggerDatabase db = new TriggerDatabase(false);
        FieldConfirmationDao dao = new FieldConfirmationDao(db);

        FieldConfirmationPolicyDto enabled = dao.setPolicy(command(true, 12, null, EMPTY));
        assertAll(
                () -> assertTrue(enabled.requiresConfirmation(), "The enabled policy must be returned after commit."),
                () -> assertEquals(12, enabled.requiredFirmWideRoleDefinitionId()),
                () -> assertEquals(1, enabled.policyRevision()));

        FieldConfirmationPolicyDto disabled = dao.setPolicy(command(false, null, enabled.id(), enabled.rowVer()));
        assertAll(
                () -> assertFalse(disabled.requiresConfirmation(), "Disabling must persist as a successor policy."),
                () -> assertNull(disabled.requiredFirmWideRoleDefinitionId()),
                () -> assertEquals(2, disabled.policyRevision()),
                () -> assertEquals(2, db.committed.size(), "The superseded revision must remain immutable history."),
                () -> assertNotNull(db.committed.get(0).supersededAt),
                () -> assertNull(db.committed.get(1).supersededAt));
    }

    @Test void staleRowVersionRejectsMutationAndLeavesCurrentRevisionUnchanged() {
        TriggerDatabase db = new TriggerDatabase(false);
        FieldConfirmationDao dao = new FieldConfirmationDao(db);
        FieldConfirmationPolicyDto current = dao.setPolicy(command(true, 12, null, EMPTY));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dao.setPolicy(command(false, null, current.id(), new byte[]{99})));

        assertAll(
                () -> assertTrue(error.getMessage().contains("changed; reload")),
                () -> assertEquals(1, db.committed.size()),
                () -> assertNull(db.committed.get(0).supersededAt),
                () -> assertTrue(db.events.contains("rollback")),
                () -> assertEquals(1, db.auditCount));
    }

    @Test void auditFailureRollsBackInsertedPolicyAndIdentityReadback() {
        TriggerDatabase db = new TriggerDatabase(true);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new FieldConfirmationDao(db).setPolicy(command(true, 12, null, EMPTY)));

        assertAll(
                () -> assertTrue(error.getMessage().contains("Policy mutation failed")),
                () -> assertTrue(db.committed.isEmpty(), "The policy insert must roll back with its failed audit."),
                () -> assertEquals(List.of("begin", "insert:1", "audit", "rollback", "restore-auto", "close"), db.events));
    }

    private static SetFieldConfirmationPolicyCommand command(boolean required, Integer role, Long id, byte[] rowVer) {
        return new SetFieldConfirmationPolicyCommand(7, 9, 44, required, role, id, rowVer);
    }

    private static final class TriggerDatabase implements DbSessionProvider {
        private List<PolicyRow> committed = new ArrayList<>();
        private List<PolicyRow> working;
        private long nextId = 1;
        private int nextVersion = 1;
        private int auditCount;
        private final boolean failAudit;
        private final List<String> events = new ArrayList<>();

        private TriggerDatabase(boolean failAudit) { this.failAudit = failAudit; }

        @Override public Connection requireConnection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                case "setAutoCommit" -> { boolean auto = (boolean) args[0]; if (!auto) { working = copy(committed); events.add("begin"); } else events.add("restore-auto"); yield null; }
                case "prepareStatement" -> statement((String) args[0]);
                case "commit" -> { committed = copy(working); events.add("commit"); yield null; }
                case "rollback" -> { working = copy(committed); events.add("rollback"); yield null; }
                case "close" -> { events.add("close"); yield null; }
                case "getAutoCommit" -> true;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement(String sql) throws SQLException {
            if (sql.matches("(?is).*OUTPUT\\s+INSERTED\\.Id\\s+VALUES.*") && !sql.matches("(?is).*OUTPUT\\s+INSERTED\\.Id\\s+INTO.*"))
                throw new SQLException("The target table has enabled triggers and OUTPUT has no INTO clause.");
            Map<Integer, Object> binds = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                case "setInt", "setLong", "setString", "setBoolean", "setBytes", "setNull", "setTimestamp" -> { binds.put((Integer) args[0], method.getName().equals("setNull") ? null : args[1]); yield null; }
                case "executeQuery" -> query(sql, binds);
                case "executeUpdate" -> update(sql, binds);
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            });
        }

        private ResultSet query(String sql, Map<Integer, Object> b) {
            if (sql.contains("FROM dbo.Users WHERE id=")) return rows(new Object[][]{{1}});
            if (sql.contains("WITH candidates AS")) return rows(new Object[][]{{44, "SOL"}});
            if (sql.contains("FROM dbo.FirmWideRoleDefinitions WHERE Id=")) return rows(new Object[][]{{1}});
            if (sql.contains("WITH(UPDLOCK,HOLDLOCK)")) {
                return rows(working.stream().filter(r -> r.supersededAt == null).map(PolicyRow::policyColumns).toArray(Object[][]::new));
            }
            if (sql.contains("DECLARE @InsertedPolicy")) {
                PolicyRow row = new PolicyRow(nextId++, 7, (String) b.get(2), (Long) b.get(3), (Boolean) b.get(4), (Integer) b.get(5), new byte[]{(byte) nextVersion++}, null);
                working.add(row); events.add("insert:" + row.id); return rows(new Object[][]{{row.id}});
            }
            if (sql.contains("FROM dbo.FieldConfirmationPolicies WHERE Id=")) {
                long id = ((Number) b.get(1)).longValue();
                PolicyRow row = working.stream().filter(r -> r.id == id).findFirst().orElseThrow();
                return rows(new Object[][]{row.dtoColumns()});
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private int update(String sql, Map<Integer, Object> b) throws SQLException {
            if (sql.contains("UPDATE dbo.FieldConfirmationPolicies")) {
                long id = ((Number) b.get(2)).longValue(); byte[] expected = (byte[]) b.get(4);
                for (int i = 0; i < working.size(); i++) { PolicyRow row = working.get(i); if (row.id == id && row.supersededAt == null && Arrays.equals(row.rowVer, expected)) { working.set(i, row.supersede(new byte[]{(byte) nextVersion++})); return 1; } }
                return 0;
            }
            if (sql.contains("dbo.EntityActionAuditLog")) {
                events.add("audit"); if (failAudit) throw new SQLException("audit failed"); auditCount++; return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }

        private static List<PolicyRow> copy(List<PolicyRow> source) { return new ArrayList<>(source); }
    }

    private record PolicyRow(long id, int tenant, String key, long revision, boolean required, Integer role, byte[] rowVer, Object supersededAt) {
        Object[] policyColumns() { return new Object[]{id, revision, key, required, role, rowVer}; }
        Object[] dtoColumns() { return new Object[]{id, tenant, key, revision, required, role, rowVer}; }
        PolicyRow supersede(byte[] newRowVer) { return new PolicyRow(id, tenant, key, revision, required, role, newRowVer, new Object()); }
    }

    private static ResultSet rows(Object[][] values) {
        int[] row = {-1}; boolean[] wasNull = {false};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("next")) return ++row[0] < values.length;
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("wasNull")) return wasNull[0];
            if (method.getName().startsWith("get")) {
                Object value = values[row[0]][((Integer) args[0]) - 1]; wasNull[0] = value == null;
                return switch (method.getName()) {
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                    case "getBoolean" -> value != null && (Boolean) value;
                    case "getString" -> value == null ? null : value.toString();
                    case "getBytes" -> (byte[]) value;
                    default -> value;
                };
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
