package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ContactCompleteAggregateContractTest {
 @Test void aggregateOwnsPersonalDetailsNotesAndTransactionalPhiAudit() throws Exception {
  String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));
  assertTrue(source.contains("DateOfBirth=?,Condition=?,Notes=?,IsDeceased=?"));
  assertTrue(source.contains("phi.auditUpdate(con,c.actorUserId(),\"Contacts\",\"Condition\""));
  assertTrue(source.indexOf("updateStructuredContact(con,c)")<source.indexOf("applyPhones(con,c,phones)"));
  assertFalse(source.contains("updateBasicProfile"));
 }
}
