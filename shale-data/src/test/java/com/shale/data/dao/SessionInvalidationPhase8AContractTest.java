package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
class SessionInvalidationPhase8AContractTest{
	@Test void disableRemovalAndCredentialResetRevokeDurableSessionsInTheirTransactions()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/UserDao.java"));assertTrue(count(s,"revokeAllSessions(con,")>=4);assertTrue(s.contains("RevocationReason='SECURITY'"));assertTrue(s.contains("AND RevokedAt IS NULL"));}
	@Test void boundValidationAlsoRequiresCurrentAccountEligibility()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/UserSessionDao.java"));assertTrue(s.contains("JOIN dbo.Users u"));assertTrue(s.contains("COALESCE(u.is_deleted,0)=0"));assertTrue(s.contains("COALESCE(u.IsRemoved,0)=0"));}
	private static int count(String s,String x){int n=0,p=0;while((p=s.indexOf(x,p))>=0){n++;p+=x.length();}return n;}
}
