package com.shale.data.dao;

import com.shale.core.dto.MigratedCaseDateProjectionDto;
import com.shale.core.service.CaseServicePort;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigratedCaseDateProjectionContractTest {
    private static final String[] LEGACY_COLUMNS = {
            "CallerDate", "CallerTime", "DateOfInjury", "DateOfMedicalNegligence",
            "DateMedicalNegligenceWasDiscovered", "StatuteOfLimitations", "TortNoticeDeadline",
            "DiscoveryDeadline", "DateFeeAgreementSigned", "DateNonEngagementLetterSent"
    };

    @Test void sqlIsAuthoritativeTenantSafeReadOnlyAndLabelIndependent() {
        String sql = CaseDateDao.migratedProjectionSql("?,?,?");
        assertTrue(sql.contains("FROM dbo.Cases c"));
        assertTrue(sql.contains("dbo.CaseDates cd"));
        assertTrue(sql.contains("cd.ShaleClientId = c.ShaleClientId"));
        assertTrue(sql.contains("c.ShaleClientId = ?"));
        assertTrue(sql.contains("cd.IsDeleted = 0"));
        assertTrue(sql.contains("COALESCE(eff.SystemKey, st.SystemKey)"), "historical stored type is the fallback");
        assertTrue(sql.contains("t.IsDeleted = 0 AND t.IsActive = 1"));
        assertTrue(sql.contains("CASE WHEN t.ShaleClientId = ? THEN 0 ELSE 1 END"));
        assertFalse(sql.contains("Name"), "display labels cannot identify fixed meanings");
        assertFalse(sql.contains("CalendarEvents"));
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE)\\b.*"));
        for (String column : LEGACY_COLUMNS) assertFalse(sql.contains(column), column);
    }

    @Test void serviceBoundaryIsCollectionOrientedAndFrameworkNeutral() throws Exception {
        Method method = CaseServicePort.class.getMethod("projectMigratedCaseDates", Collection.class, int.class, int.class);
        assertEquals(Map.class, method.getReturnType());
        String dto = Files.readString(Path.of("../shale-core/src/main/java/com/shale/core/dto/MigratedCaseDateProjectionDto.java"));
        assertFalse(dto.contains("javafx"));
        assertFalse(dto.contains("report"));
        assertFalse(dto.contains("spreadsheet"));
        assertFalse(dto.contains("RowVer"));
        assertFalse(dto.contains("occurrenceId"));
    }

    @Test void implementationCoalescesDuplicatesAndChunksInsteadOfQueryingPerCase() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertTrue(source.contains("LinkedHashSet<Long> requested"));
        assertEquals(500, CaseDateDao.PROJECTION_BATCH_SIZE);
        assertTrue(source.contains("offset += PROJECTION_BATCH_SIZE"));
        assertTrue(source.contains("readMigratedProjectionBatch(con, ids.subList"));
        assertFalse(source.contains("for (long id : ids) projectMigratedCaseDates"));
    }

    @Test void enforcementIsNarrowAndDeferredReadersRemainExplicitlyDeferred() throws Exception {
        String inventory = Files.readString(Path.of("../architecture/case-dates-runtime-cutover-inventory.md"));
        assertTrue(inventory.contains("Cases grid, board, and export"));
        assertTrue(inventory.contains("intentionally not converted"));
        String dao = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        int start = dao.indexOf("static String migratedProjectionSql");
        int end = dao.indexOf("public Map<MigratedCaseDateKey, CaseDateDto>", start);
        String boundary = dao.substring(start, end);
        for (String column : LEGACY_COLUMNS) assertFalse(boundary.contains(column), column);
        assertFalse(boundary.contains("CalendarEvents"));
        assertFalse(boundary.contains("INSERT "));
        assertFalse(boundary.contains("UPDATE "));
        assertFalse(boundary.contains("DELETE "));
    }
}
