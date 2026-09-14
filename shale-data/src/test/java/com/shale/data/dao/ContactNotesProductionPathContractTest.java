package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ContactNotesProductionPathContractTest {
    @Test void authoritativeReadAndAggregateWriteCarryNotesWithoutLegacyColumns() throws Exception {
        String read = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
        String mutation = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));
        assertTrue(read.contains("c.DateOfBirth,c.Condition,c.Notes,c.IsDeceased,c.UpdatedAt"));
        assertTrue(read.contains("rs.getString(\"Notes\")"));
        assertTrue(mutation.contains("Condition=?,Notes=?,IsDeceased=?"));
        assertTrue(mutation.contains("p.setString(i++,normalizeNotes(c.notes()))"));
        assertTrue(mutation.contains("Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND"));
        assertTrue(mutation.contains("UpdatedAt=?"));
        assertTrue(mutation.contains("c.notes().length()>CONTACT_NOTES_MAX_CHARS"));
        assertTrue(mutation.contains("value==null||value.isBlank()?null:value"));
        for (String legacy : new String[] {"PhoneCell", "PhoneHome", "PhoneWork", "EmailPersonal",
                "EmailWork", "EmailOther", "AddressHome", "AddressWork", "AddressOther", "IsExpert"})
            assertFalse(mutation.contains(legacy));
    }
}
