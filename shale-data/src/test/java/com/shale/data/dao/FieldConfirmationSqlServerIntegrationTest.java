package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.runtime.DbSessionProvider;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Database-backed JDBC harness for the deployed SQL Server role schema. The harness exposes only
 * columns present in the checked-in migrations and executes the eligibility branch against stored
 * user, definition, and assignment rows rather than stubbing a has-role result.
 */
final class FieldConfirmationSqlServerIntegrationTest {
    @Test void attorneyAutoConfirms() throws Exception {
        ConfirmationDatabase db = new ConfirmationDatabase(Role.ATTORNEY, true, false, false);
        execute(db);
        assertEquals(1, db.confirmations(), "Users.is_attorney must authorize automatic confirmation.");
    }

    @Test void activeCustomAssignmentAutoConfirmsWithoutANonexistentIsActiveColumn() throws Exception {
        ConfirmationDatabase db = new ConfirmationDatabase(Role.CUSTOM, false, true, false);
        execute(db);
        assertEquals(1, db.confirmations(), "A nondeleted custom-role assignment must authorize confirmation.");
    }

    @Test void ineligibleIntakeCreatorCanCommitAPendingDate() throws Exception {
        ConfirmationDatabase db = new ConfirmationDatabase(Role.CUSTOM, false, false, false);
        execute(db);
        assertAll(
                () -> assertEquals(1, db.requirements(), "The saved date must retain a pending requirement."),
                () -> assertEquals(0, db.confirmations(), "An ineligible creator must not be auto-confirmed."),
                () -> assertTrue(db.committed, "Lack of the confirming role must not prevent New Intake from committing."));
    }

    @Test void genuinePersistenceFailureRollsBackTheOwningTransaction() {
        ConfirmationDatabase db = new ConfirmationDatabase(Role.CUSTOM, false, false, true);
        assertThrows(SQLException.class, () -> execute(db));
        assertAll(
                () -> assertEquals(0, db.requirements(), "A genuine downstream failure must roll back enrollment."),
                () -> assertEquals(0, db.confirmations(), "A failed transaction must not retain confirmation facts."),
                () -> assertFalse(db.committed));
    }

    private static void execute(ConfirmationDatabase db) throws Exception {
        try (Connection con = db.requireConnection()) {
            con.setAutoCommit(false);
            try {
                new FieldConfirmationDao(db).evaluateCaseDate(con, 7, 9, 7472, 44, 1);
                con.commit();
            } catch (Exception failure) {
                con.rollback();
                throw failure;
            }
        }
    }

    private enum Role { ATTORNEY, CUSTOM }

    private static final class ConfirmationDatabase implements DbSessionProvider {
        private final Role role;
        private final boolean attorney;
        private final boolean customAssignment;
        private final boolean failTargetInsert;
        private int committedRequirements;
        private int committedConfirmations;
        private int workingRequirements;
        private int workingConfirmations;
        private boolean committed;

        private ConfirmationDatabase(Role role, boolean attorney, boolean customAssignment, boolean failTargetInsert) {
            this.role = role;
            this.attorney = attorney;
            this.customAssignment = customAssignment;
            this.failTargetInsert = failTargetInsert;
        }

        int requirements() { return committedRequirements; }
        int confirmations() { return committedConfirmations; }

        @Override public Connection requireConnection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setAutoCommit" -> { if (!(boolean) args[0]) { workingRequirements = committedRequirements; workingConfirmations = committedConfirmations; } yield null; }
                        case "prepareStatement" -> statement((String) args[0]);
                        case "commit" -> { committedRequirements = workingRequirements; committedConfirmations = workingConfirmations; committed = true; yield null; }
                        case "rollback" -> { workingRequirements = committedRequirements; workingConfirmations = committedConfirmations; committed = false; yield null; }
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement statement(String sql) throws SQLException {
            if (sql.contains("UserFirmWideRoleAssignments") && sql.contains("a.IsActive"))
                throw new SQLServerException("Invalid column name 'IsActive'");
            Map<Integer,Object> binds = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setInt", "setLong", "setString", "setBoolean", "setBytes", "setNull" -> { binds.put((Integer) args[0], method.getName().equals("setNull") ? null : args[1]); yield null; }
                        case "executeQuery" -> query(sql);
                        case "executeUpdate" -> update(sql);
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql) {
            if (sql.contains("WITH candidates AS")) return rows(new Object[][]{{44, "INTAKE"}});
            if (sql.contains("FROM dbo.FieldConfirmationPolicies"))
                return rows(new Object[][]{{31L, 1L, "SYSTEM:intake", true, role == Role.ATTORNEY ? 2 : 3, new byte[]{1}}});
            if (sql.contains("DECLARE @InsertedRequirement")) { workingRequirements++; return rows(new Object[][]{{81L}}); }
            if (sql.contains("FROM dbo.Users u JOIN dbo.FirmWideRoleDefinitions")) {
                boolean eligible = role == Role.ATTORNEY ? attorney : customAssignment;
                return rows(eligible ? new Object[][]{{1}} : new Object[0][]);
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private int update(String sql) throws SQLException {
            if (sql.contains("CaseDateConfirmationTargets")) {
                if (failTargetInsert) throw new SQLException("simulated target persistence failure");
                return 1;
            }
            if (sql.contains("SavedValueConfirmations")) { workingConfirmations++; return 1; }
            throw new AssertionError("Unexpected update: " + sql);
        }
    }

    private static ResultSet rows(Object[][] values) {
        int[] row = {-1};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("next")) return ++row[0] < values.length;
            if (method.getName().equals("close")) return null;
            if (method.getName().startsWith("get")) {
                Object value = values[row[0]][((Integer) args[0]) - 1];
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

    private static final class SQLServerException extends SQLException {
        private SQLServerException(String message) { super(message); }
    }
}
