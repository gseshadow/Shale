package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class UserDictionaryMigrationContractTest {
 @Test void migrationEnforcesScopeUniquenessAuditColumnsAndRls() throws Exception {
  String sql=Files.readString(Path.of("../docs/sql/2026-09-01_user_dictionary_words_foundation.sql"));
  assertAll(()->assertTrue(sql.contains("UserDictionaryWords")),()->assertTrue(sql.contains("ShaleClientId,UserId,NormalizedWord")),
   ()->assertTrue(sql.contains("RowVer rowversion")),()->assertTrue(sql.contains("FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id)")),
   ()->assertTrue(sql.contains("fn_FilterByTenant(ShaleClientId) ON dbo.UserDictionaryWords")),()->assertTrue(sql.contains("IF OBJECT_ID")),()->assertTrue(sql.contains("IF NOT EXISTS")));
 }
}
