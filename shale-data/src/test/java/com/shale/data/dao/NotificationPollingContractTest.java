package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationPollingContractTest {
	@Test void highWaterAndCursorQueriesAreTenantUserScopedAndIdOrdered() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/NotificationDao.java"));
		assertTrue(source.contains("MAX(n.Id)"));
		assertTrue(source.contains("n.ShaleClientId=? AND n.UserId=?"));
		assertTrue(source.contains("n.Id>?"));
		assertTrue(source.contains("ORDER BY n.Id ASC"));
		assertTrue(source.contains("CASE WHEN ISNULL(n.IsDismissed,0)=0"));
		assertTrue(source.contains("nextScannedId"));
		assertTrue(source.contains("scannedIds.size()>limit"));
	}
}
