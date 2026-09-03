package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.shale.core.dto.CaseDetailDto;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseDetailsTimelineWriterTest {
    @Test
    void changedOverviewDetailWritesOneTenantCaseActorScopedEntry() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        Connection connection = connection(rows);

        CaseDetailsTimelineWriter.appendChanges(connection, 42, 7, 9,
                detail("Old name", "private old description"),
                detail("New name", "private old description"));

        assertEquals(1, rows.size());
        assertEquals(List.of(42L, 7, CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED, 9,
                "changed Case Name", "from Old name to New name"), projected(rows.get(0)));
    }

    @Test
    void overviewCaseNumberNameAndDescriptionChangesUseTheRuntimeWriter() throws Exception {
        List<List<Object>> rows = new ArrayList<>();

        CaseDetailsTimelineWriter.appendChanges(connection(rows), 42, 7, 9,
                detail("Old name", "N-1", "private old description"),
                detail("Old name", "N-2", "private old description"));
        assertEquals(CaseDao.CaseTimelineEventTypes.CASE_NUMBER_CHANGED, rows.get(0).get(2));
        assertEquals("from N-1 to N-2", rows.get(0).get(6));

        rows.clear();
        CaseDetailsTimelineWriter.appendChanges(connection(rows), 42, 7, 9,
                detail("Old name", "N-1", "private old description"),
                detail("New name", "N-1", "private old description"));
        assertEquals(CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED, rows.get(0).get(2));
        assertEquals("from Old name to New name", rows.get(0).get(6));

        rows.clear();
        CaseDetailsTimelineWriter.appendChanges(connection(rows), 42, 7, 9,
                detail("Old name", "N-1", "private old description"),
                detail("Old name", "N-1", "private new description"));
        assertEquals(CaseDao.CaseTimelineEventTypes.DESCRIPTION_CHANGED, rows.get(0).get(2));
        assertNull(rows.get(0).get(6), "Description content must never be copied to Timeline");
    }

    @Test
    void combinedOverviewSaveWritesOneEntryPerChangedFieldWithoutDuplicates() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        CaseDetailsTimelineWriter.appendChanges(connection(rows), 42, 7, 9,
                detail("Old name", "N-1", "private old description"),
                detail("New name", "N-2", "private new description"));

        assertEquals(List.of(
                CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED,
                CaseDao.CaseTimelineEventTypes.CASE_NUMBER_CHANGED,
                CaseDao.CaseTimelineEventTypes.DESCRIPTION_CHANGED),
                rows.stream().map(row -> row.get(2)).toList());
        rows.forEach(row -> {
            assertEquals(42L, row.get(0));
            assertEquals(7, row.get(1));
            assertEquals(9, row.get(4));
        });
    }

    @Test
    void unchangedSaveWritesNoEntryAndSensitiveDetailsAreRedacted() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        Connection connection = connection(rows);
        CaseDetailDto baseline = detail("Same name", "N-1", "private old description");

        CaseDetailsTimelineWriter.appendChanges(connection, 42, 7, 9, baseline, baseline);
        assertEquals(0, rows.size());

        CaseDetailsTimelineWriter.appendChanges(connection, 42, 7, 9, baseline,
                detail("Same name", "N-1", "private new description"));
        assertEquals(1, rows.size());
        assertEquals(CaseDao.CaseTimelineEventTypes.DESCRIPTION_CHANGED, rows.get(0).get(2));
        assertEquals(null, rows.get(0).get(6));
    }

    @Test
    void normalizationOnlyOverviewSaveWritesNoEntry() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        CaseDetailsTimelineWriter.appendChanges(connection(rows), 42, 7, 9,
                detail("Case name", "N-1", "description"),
                detail("  Case name  ", " N-1 ", " description "));
        assertEquals(0, rows.size());
    }

    private static List<Object> projected(List<Object> row) {
        return List.of(row.get(0), row.get(1), row.get(2), row.get(4), row.get(5), row.get(6));
    }

    private static Connection connection(List<List<Object>> rows) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement") ? statement(rows) : defaultValue(method.getReturnType()));
    }

    private static PreparedStatement statement(List<List<Object>> rows) {
        List<Object> values = new ArrayList<>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) {
                        int index = (Integer) args[0];
                        while (values.size() < index) values.add(null);
                        values.set(index - 1, args[1]);
                    }
                    if (method.getName().equals("executeUpdate")) { rows.add(new ArrayList<>(values)); return 1; }
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

    private static CaseDetailDto detail(String name, String description) {
        return detail(name, "N-1", description);
    }

    private static CaseDetailDto detail(String name, String number, String description) {
        return new CaseDetailDto(42, number, name, description, "Open", null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, LocalDateTime.now(), new byte[]{1});
    }
}
