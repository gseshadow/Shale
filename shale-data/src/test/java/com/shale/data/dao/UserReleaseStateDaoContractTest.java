package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class UserReleaseStateDaoContractTest {
	private static String source()throws Exception{return Files.readString(Path.of("src/main/java/com/shale/data/dao/UserReleaseStateDao.java"));}
	@Test void readIsCurrentUserTenantScopedAndDoesNotCreateState()throws Exception{String s=source();assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId')")&&s.contains("SESSION_CONTEXT(N'PrincipalUserId')"));assertTrue(s.contains("s.ShaleClientId=? AND s.UserId=? AND s.ClientType=? AND s.ReleaseChannel=?"));assertEquals(1,count(s,"INSERT dbo.UserReleaseState"));}
	@Test void acknowledgementIsOneDaoOwnedTransactionWithNumericMonotonicRules()throws Exception{String s=source();assertTrue(s.contains("setAutoCommit(false)")&&s.contains("c.commit()")&&s.contains("c.rollback()"));assertTrue(s.contains("target.version.compareTo(old.get().version())"));assertTrue(s.contains("Acknowledged release cannot move backwards"));assertTrue(s.contains("order==0"),"same release must return the persisted row without update");assertTrue(s.contains("UPDLOCK,HOLDLOCK"));}
	@Test void rejectsUnknownDraftWrongChannelAndStaleState()throws Exception{String s=source();assertTrue(s.contains("Target release does not exist"));assertTrue(s.contains("PublicationStatus.PUBLISHED"));assertTrue(s.contains("target.channel!=channel"));assertTrue(s.contains("Arrays.equals(expected,old.get().rowVersion())"));assertTrue(s.contains("WHERE Id=? AND ShaleClientId=? AND UserId=? AND RowVer=?"));}
	@Test void firstCreateRaceAndScopeVocabularyAreHandled()throws Exception{String s=source();assertTrue(s.contains("getErrorCode()==2601||e.getErrorCode()==2627"));assertTrue(s.contains("created concurrently"));assertTrue(s.contains("ClientType type,ReleaseChannel channel"));assertFalse(s.contains("ApplicationInstance"));assertFalse(s.contains("UserSession"));}
	private static int count(String value,String part){int n=0,p=0;while((p=value.indexOf(part,p))>=0){n++;p+=part.length();}return n;}
}
