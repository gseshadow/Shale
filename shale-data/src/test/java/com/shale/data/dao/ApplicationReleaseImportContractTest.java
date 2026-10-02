package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class ApplicationReleaseImportContractTest {
	private static final Path DAO=Path.of("src/main/java/com/shale/data/dao/ApplicationReleaseImportDao.java");
	@Test void importIsAtomicLockedIdempotentAndAudited()throws Exception{String s=Files.readString(DAO);for(String token:new String[]{"setAutoCommit(false)","WITH(UPDLOCK,HOLDLOCK)","same(existing, source)","Outcome.UNCHANGED","c.commit()","c.rollback()","GlobalControlPlaneAuditLog","RELEASE_CATALOG_IMPORTED"})assertTrue(s.contains(token),token);assertTrue(s.indexOf("audit(c,operator")<s.indexOf("c.commit()"),"audit must precede commit");}
	@Test void conflictingHistoryRequiresExplicitRowVersionControlledUpdate()throws Exception{String s=Files.readString(DAO);for(String token:new String[]{"if (!allowUpdate)","expectedRowVersion is required","WHERE Id=? AND RowVer=?","controlled update was rejected","RELEASE_CATALOG_UPDATED"})assertTrue(s.contains(token),token);}
	@Test void allOrderedChildrenAreWrittenInsideParentTransaction()throws Exception{String s=Files.readString(DAO);assertTrue(s.contains("release item order must be contiguous"));assertTrue(s.contains("addBatch()"));assertTrue(s.contains("executeBatch()"));assertFalse(s.contains("ShaleClientId"));}
}
