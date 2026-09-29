package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
class UserSessionDaoContractTest{
	@Test void persistenceUsesOwnerScopeDatabaseUtcAndNoRawSecrets()throws Exception{String s=Files.readString(Path.of("src","main","java","com","shale","data","dao","UserSessionDao.java"));for(String x:new String[]{"verifyOwner(c,tenant,user,type,instance)","SESSION_CONTEXT(N'ShaleClientId')","SESSION_CONTEXT(N'PrincipalUserId')","ai.ShaleClientId=? AND ai.UserId=?","RevokedAt=COALESCE(RevokedAt,SYSUTCDATETIME())","CurrentAccessJti=?","AND CurrentAccessJti=? AND RevokedAt IS NULL"})assertTrue(s.contains(x),x);for(String x:new String[]{"AccessToken","RefreshToken","Password","nvarchar(max)"})assertFalse(s.contains(x),x);}
}
