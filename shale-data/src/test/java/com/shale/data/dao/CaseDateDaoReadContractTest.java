package com.shale.data.dao;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaseDateDaoReadContractTest {
    @Test void effectiveSelectorSqlUsesModernOverlayResetAndOrderingContract() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertTrue(source.contains("ROW_NUMBER() OVER (PARTITION BY t.SystemKey"));
        assertTrue(source.contains("t.ShaleClientId = ? AND t.IsDeleted = 0 THEN 0"), "deleted tenant overrides must reset to global rather than suppress it");
        assertTrue(source.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(source.contains("UNION ALL"), "tenant-created unkeyed rows remain selectable when active");
        assertTrue(source.contains("ORDER BY SortOrder, Name, Id"));
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
        assertFalse(sql.contains("CalendarEvents"));
        assertFalse(sql.contains("UPDATE dbo.Cases"));
        assertFalse(sql.contains("StatuteOfLimitations"));
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
