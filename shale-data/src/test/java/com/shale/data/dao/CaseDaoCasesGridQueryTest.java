package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCasesGridQueryTest {

    @Test
    void casesGridQueryUsesCurrentIncidentDateColumn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertFalse(source.contains("IncidentOccurred"),
                "Cases grid queries must not reference IncidentOccurred unless schema detection guards it");
        assertTrue(source.contains("c.DateOfInjury AS DateOfIncident"),
                "Cases grid should hydrate Date of Incident from the existing DateOfInjury column");
        assertTrue(source.contains("c.StatuteOfLimitations"),
                "Cases grid should hydrate Statute of Limitations from the existing StatuteOfLimitations column");
        assertTrue(source.contains("c.TortNoticeDeadline"),
                "Cases grid should hydrate Tort Claims Notice Deadline from the existing TortNoticeDeadline column");
    }

    @Test
    void casesGridQueryUsesDescriptionSource() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertTrue(source.contains("c.Description AS Description"),
                "Cases grid should expose Description from dbo.Cases.Description");
    }

    @Test
    void casesGridQueryHydratesLatestUpdateFromCaseUpdates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertTrue(source.contains("FROM dbo.CaseUpdates cu"));
        assertTrue(source.contains("cu.NoteText"));
        assertTrue(source.contains("ORDER BY cu.CreatedAt DESC, cu.Id DESC"));
    }
}
