package com.shale.data.dao;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.MigratedCaseDateKey;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaseDateDaoReadContractTest {
    @Test void protectedOverviewDatesAreClassifiedByEffectiveSemanticIdentity() {
        Map<Integer, MigratedCaseDateKey> protectedTypes = Map.of(
                701, MigratedCaseDateKey.STATUTE_OF_LIMITATIONS,
                702, MigratedCaseDateKey.TORT_NOTICE_DEADLINE);

        assertEquals(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS,
                CaseDateDao.migratedOccurrenceKey(701, null, protectedTypes));
        assertEquals(MigratedCaseDateKey.TORT_NOTICE_DEADLINE,
                CaseDateDao.migratedOccurrenceKey(702, "tenant_custom_deadline", protectedTypes));
        assertNull(CaseDateDao.migratedOccurrenceKey(999, "statute_of_limitations", protectedTypes),
                "a legacy-key row that is not the active protected mapping is not authoritative");
        assertEquals(MigratedCaseDateKey.DATE_OF_INJURY,
                CaseDateDao.migratedOccurrenceKey(703, "date_of_injury", protectedTypes));
    }

    @Test void effectiveSelectorSqlUsesModernOverlayResetAndOrderingContract() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertTrue(source.contains("ROW_NUMBER() OVER (PARTITION BY t.SystemKey"));
        assertTrue(source.contains("t.ShaleClientId = ? AND t.IsDeleted = 0 THEN 0"), "deleted tenant overrides must reset to global rather than suppress it");
        assertTrue(source.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(source.contains("UNION ALL"), "tenant-created unkeyed rows remain selectable when active");
        assertTrue(source.contains("ORDER BY SortOrder, Name, Id"));
        assertTrue(source.contains("pm.CaseDateTypeId=t.Id AND pm.ShaleClientId IS NULL"),
                "global ownership alone must not make a nonprotected type selectable");
    }

    @Test void administrationAndMutationSelectionRestrictGlobalsToProtectedMappings() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertTrue(source.contains("WHERE t.ShaleClientId = ? OR (t.ShaleClientId IS NULL AND EXISTS"));
        assertTrue(source.contains("private static TypeRow requireSelectableType"));
        assertTrue(source.substring(source.indexOf("private static TypeRow requireSelectableType"))
                .contains("CaseDateTypeSemanticRoleMappings pm"));
        assertTrue(source.contains("requireHistoricalType"), "stored authoritative ids retain a historical read path");
    }

    @Test void occurrenceSqlPreservesHistoricalPresentationAndTenantSafety() throws Exception {
        Method m = CaseDateDao.class.getDeclaredMethod("occurrenceSql", String.class);
        m.setAccessible(true);
        String sql = (String) m.invoke(null, "cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 0");
        assertTrue(sql.contains("JOIN dbo.Cases c ON c.Id = cd.CaseId AND c.ShaleClientId = cd.ShaleClientId AND c.IsDeleted = 0"));
        assertTrue(sql.contains("JOIN dbo.CaseDateTypes st ON st.Id = cd.CaseDateTypeId AND (st.ShaleClientId = cd.ShaleClientId OR st.ShaleClientId IS NULL)"));
        assertTrue(sql.contains("OUTER APPLY"));
        assertTrue(sql.contains("t.IsDeleted = 0 AND t.IsActive = 1"));
        assertTrue(sql.contains("COALESCE(eff.Name, st.Name) AS TypeName"));
        assertTrue(sql.contains("LEFT JOIN dbo.Users cu ON cu.Id = cd.CreatedByUserId AND cu.ShaleClientId = cd.ShaleClientId"));
        assertTrue(sql.contains("cu.name_first"));
        assertTrue(sql.contains("cu.name_last"));
        assertTrue(sql.contains("uu.name_first"));
        assertTrue(sql.contains("uu.name_last"));
        assertFalse(sql.contains("cu.DisplayName"));
        assertFalse(sql.contains("cu.first_name"));
        assertFalse(sql.contains("cu.last_name"));
        assertFalse(sql.contains("uu.DisplayName"));
        assertFalse(sql.contains("uu.first_name"));
        assertFalse(sql.contains("uu.last_name"));
        assertFalse(sql.contains("CalendarEvents"));
        assertFalse(sql.contains("UPDATE dbo.Cases"));
        assertFalse(sql.contains("StatuteOfLimitations"));
    }


    @Test void assembledActiveAndRemovedOccurrenceSqlKeepClauseBoundariesSeparated() throws Exception {
        Method m = CaseDateDao.class.getDeclaredMethod("occurrenceSql", String.class);
        m.setAccessible(true);
        String active = (String) m.invoke(null, "cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 0 ORDER BY cd.StartsAt, cd.EndsAt, COALESCE(eff.SortOrder, st.SortOrder), COALESCE(eff.Name, st.Name), cd.Id");
        String removed = (String) m.invoke(null, "cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 1 ORDER BY cd.StartsAt, cd.EndsAt, COALESCE(eff.SortOrder, st.SortOrder), COALESCE(eff.Name, st.Name), cd.Id");

        assertAssembledOccurrenceSql(active, "cd.IsDeleted = 0");
        assertAssembledOccurrenceSql(removed, "cd.IsDeleted = 1");
    }

    private static void assertAssembledOccurrenceSql(String sql, String deletedPredicate) {
        assertTrue(sql.contains("WHERE\n") || sql.contains("WHERE\r\n"), "WHERE must be separated from the supplied predicate");
        assertTrue(sql.contains("WHERE\n" + deletedPredicate) || sql.contains("WHERE\r\n" + deletedPredicate) || sql.contains("WHERE\ncd.CaseId") || sql.contains("WHERE\r\ncd.CaseId"));
        assertTrue(sql.contains(deletedPredicate), deletedPredicate);
        assertFalse(sql.contains("WHEREcd"));
        assertFalse(sql.contains("JOINdbo"));
        assertFalse(sql.contains("ONcd"));
        assertFalse(sql.contains("ANDcd"));
        assertFalse(sql.contains("ORDERBY"));
        assertFalse(sql.contains("BYcd"));
        assertTrue(sql.contains("JOIN dbo.Cases"));
        assertTrue(sql.contains("JOIN dbo.CaseDateTypes"));
        assertTrue(sql.contains("LEFT JOIN dbo.Users cu"));
        assertTrue(sql.contains("LEFT JOIN dbo.Users uu"));
        assertTrue(sql.contains("ORDER BY cd.StartsAt"));
        assertTrue(sql.contains("cu.name_first"));
        assertTrue(sql.contains("cu.name_last"));
        assertTrue(sql.contains("uu.name_first"));
        assertTrue(sql.contains("uu.name_last"));
        assertFalse(sql.contains("cu.DisplayName"));
        assertFalse(sql.contains("uu.DisplayName"));
        assertFalse(sql.contains("cu.first_name"));
        assertFalse(sql.contains("cu.last_name"));
        assertFalse(sql.contains("uu.first_name"));
        assertFalse(sql.contains("uu.last_name"));
    }

    @Test void dtoRowVersionsAreDefensiveCopies() {
        byte[] typeRv = {1, 2, 3};
        EffectiveCaseDateTypeDto type = new EffectiveCaseDateTypeDto(1, null, "trial", "Trial", null, "TRIAL", "#B91C1C", true, 1, true, false, EffectiveCaseDateTypeDto.Origin.GLOBAL, typeRv);
        typeRv[0] = 9;
        assertEquals(1, type.rowVer()[0]);
        byte[] returnedTypeRv = type.rowVer();
        returnedTypeRv[1] = 9;
        assertEquals(2, type.rowVer()[1]);

        byte[] dateRv = {4, 5, 6};
        CaseDateDto date = new CaseDateDto(10, 7, 20, 1, "trial", "Trial", null, "TRIAL", "#B91C1C", true,
                LocalDateTime.of(2026, 8, 4, 9, 30), null, false, "note", LocalDateTime.of(2026, 8, 1, 8, 0), 2, "User", null, null, null, dateRv);
        dateRv[0] = 9;
        assertEquals(4, date.rowVer()[0]);
        byte[] returnedDateRv = date.rowVer();
        returnedDateRv[1] = 9;
        assertEquals(5, date.rowVer()[1]);
    }
}
