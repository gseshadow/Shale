package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class OrganizationCardPresentationContractTest {
 @Test void bulkProjectionIsBoundedTenantScopedAndFixedQueryCount() throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/OrganizationDao.java"));
  String method=source.substring(source.indexOf("findCardPresentations("),source.indexOf("private static Map<Integer,String> loadCardScalar"));
  assertTrue(method.contains("limit(100)"));assertTrue(method.contains("verifyTenantMatchesSession"));
  assertTrue(method.contains("ShaleClientId=?"));assertTrue(method.contains("OrganizationId IN ("));
  assertFalse(method.contains("findStructuredContactProfile"));
  assertTrue(method.contains("IsDeleted=0"));
 }
}
