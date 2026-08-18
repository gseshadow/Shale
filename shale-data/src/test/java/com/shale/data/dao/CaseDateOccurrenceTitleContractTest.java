package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateOccurrenceTitleContractTest {
    @Test void authoritativePathNormalizesValidatesPersistsAndReadsTitleWithoutAuditingIt() throws Exception {
        String dao=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertAll(
            () -> assertTrue(dao.contains("String title=normalizeTitle(c.title())")),
            () -> assertTrue(dao.contains("title.length()>255")),
            () -> assertTrue(dao.contains("CaseDateTypeId, Title, StartsAt")),
            () -> assertTrue(dao.contains("SET CaseDateTypeId=?, Title=?")),
            () -> assertTrue(dao.contains("cd.Title, cd.StartsAt")),
            () -> assertFalse(dao.contains("\"CaseDates\",\"Title\"")),
            () -> assertFalse(dao.contains("MetadataKey.TITLE")));
    }

    @Test void calendarUsesOccurrenceTitleFallbackButStableIdAndNoCounterpartSynchronization() throws Exception {
        String feed=Files.readString(Path.of("src/main/java/com/shale/data/dao/CalendarFeedDao.java"));
        String dates=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertAll(
            () -> assertTrue(feed.contains("CONCAT('CASE_DATE:', CAST(cd.Id AS varchar(20)))")),
            () -> assertTrue(feed.contains("NULLIF(LTRIM(RTRIM(cd.Title)), N'')")),
            () -> assertFalse(dates.contains("INSERT dbo.CalendarEvents")),
            () -> assertFalse(dates.contains("UPDATE dbo.CalendarEvents")),
            () -> assertFalse(dates.contains("Title=? AND")));
    }
}
