package com.shale.server.runtime;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
class SessionManagementContractTest{
	private static String source(String path)throws Exception{return Files.readString(Path.of("src/main/java",path));}
	@Test void serviceKeepsEveryReadAndMutationTenantQualifiedSetBasedAndAudited()throws Exception{String s=source("com/shale/server/runtime/SessionManagementService.java");for(String x:new String[]{"WHERE ShaleClientId=?","AND UserId=?","SessionId<>?","ORDER BY IssuedAt DESC,Id DESC","requireEligibleActor","requireAdmin","SessionSecurityAuditLog","c.rollback()"})assertTrue(s.contains(x),x);assertFalse(s.contains("ApplicationInstances"));assertFalse(s.contains("PubSub"));}
	@Test void publicProjectionExcludesSecretsAndInternalIdentity()throws Exception{String s=source("com/shale/server/dto/UserSessionResponse.java");for(String x:new String[]{"currentAccessJti","accessToken","rowVer","shaleClientId","long id"})assertFalse(s.contains(x),x);}
	@Test void invalidationIsStrictlyAfterCommitAndFailureIsBestEffort()throws Exception{String s=source("com/shale/server/runtime/SessionManagementService.java");assertTrue(s.contains("publishAfterCommit(actor,mutate("),"publication must consume only a successfully returned committed mutation");assertTrue(s.contains("c.commit();return List.copyOf(changed)"));assertTrue(s.contains("catch(RuntimeException failure)"));assertTrue(s.contains("OUTPUT INSERTED.SessionId"),"bulk fanout must reuse mutation output rather than N+1 reads");}
}
