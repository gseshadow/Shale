package com.shale.data.security.ai;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.shale.core.runtime.DbSessionProvider;
public class DcrConnectionFixture extends AiDcrEndpointLocalSqlServerTest {
 public static DcrConnectionFixture CURRENT;
 public DataSource ds(String role){return source(role);}
 public DbSessionProvider scoped(String role,int t,int u){return provider(role,t,u);}
 public Connection open(String role,Integer t,Integer u)throws SQLException{return connection(role,t,u);}
 public Map<String,Object> bootstrapProperties(){return Map.of("shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://localhost:"+port+";databaseName="+db,"shale.ai.oauth.bootstrap.username",prefix+"_bootstrap","shale.ai.oauth.bootstrap.password",secrets.get("bootstrap"));}
 public String databaseName(){return db;}
 public String manualToken(){return token;}
 public AiCaseDataAttestation verify(long deadline){return attestation();}
 public static void main(String[] args)throws Exception {
  var f=CURRENT=new DcrConnectionFixture();
  try {
   f.identities();f.applicationIdentities();f.proofIdentities();f.oauthIdentities();f.reviewerIdentity();f.externalIdentities();f.endpointIdentity();
   // Dedicated pretenant authentication login is fixture-owned, never a Case capability.
   for(String role:List.of("auth","onboarding")){var secret=UUID.randomUUID()+"aB9!";
   try(var c=f.owner("master")){sql(c,"CREATE LOGIN ["+f.prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");}
   f.secrets.put(role,secret);}
   f.database();f.installCompatibility();
   try(var c=f.owner(f.db)) {
    f.historical=f.receipts(c);f.immutableBinding=f.binding(c);
    for(String file:List.of("universal-pr4a-proof.sql","universal-pr4b-oauth.sql","universal-pr-a-reviewer.sql","universal-pr-b-onboarding.sql"))installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/"+file)));
    for(var entry:Map.of("cimd","AiCimdCaseRuntimeV1","cimdlife","AiCimdLifecycleV1","cimdneutral","AiCimdOAuthNeutralV1","cimdbrowser","AiCimdOAuthBrowserV1","cimdissuer","AiCimdOAuthIssuerV1","cimdresolver","AiCimdOAuthResolverV1","cimdmetadata","AiCimdMetadataPublisherV1","cimdreview","AiCimdAuthorizationCatalogV1").entrySet())sql(c,"ALTER ROLE "+entry.getValue()+" ADD MEMBER ["+f.prefix+"_"+entry.getKey()+"]");
    sql(c,"ALTER ROLE AiCimdOnboardingRuntimeV1 ADD MEMBER ["+f.prefix+"_onboarding]");
    installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-c1-external-v2.sql")));
    for(var role:ROLES.entrySet())sql(c,"ALTER ROLE "+role.getValue()+" ADD MEMBER ["+f.prefix+"_"+role.getKey()+"]");
    installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-c2-dcr-v3.sql")));
    sql(c,"ALTER ROLE AiDcrEndpointV3 ADD MEMBER ["+f.prefix+"_v3endpoint]");
    for(String batch:Files.readString(Path.of("../docs/sql/2026-10-14_pretenant_authentication_modules.sql")).split("(?im)^GO\\s*$"))if(!batch.isBlank())installSql(c,batch);
    sql(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ["+f.prefix+"_auth]; GRANT INSERT,UPDATE ON dbo.UserSessions TO ["+f.prefix+"_runtime]; GRANT SELECT ON dbo.ApplicationInstances TO ["+f.prefix+"_runtime]");
    String pw=Files.readString(Path.of("../work/pr-c2-sql-runtime/browser-password"));
    String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(pw);
    try(var p=c.prepareStatement("UPDATE dbo.Users SET password_hash=? WHERE Id IN(71,72,81,82)")){p.setString(1,hash);p.executeUpdate();}
    sql(c,"UPDATE dbo.AiExternalControlV2 SET Enabled=1;UPDATE dbo.AiDcrControlV3 SET Enabled=1;UPDATE dbo.AiProductCapability SET Enabled=1;UPDATE dbo.AiCimdProofControlV1 SET Enabled=1;UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1");
   }
   f.successor=true;f.externalAttestation().verify();
   try(var c=f.owner(f.db)){if(count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'")!=0)throw new IllegalStateException("Fresh DCR registry required");}
   try(var c=f.owner(f.db);var st=c.createStatement();var rows=st.executeQuery("SELECT r.ClientId FROM dbo.AiIntegrationRegistration r WHERE r.ShaleClientId=7 AND NOT EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WHERE p.ShaleClientId=r.ShaleClientId AND p.IntegrationId=r.IntegrationId)")) {
    if(!rows.next())throw new IllegalStateException("Existing manual registration required");
    Files.writeString(Path.of("../work/pr-c2-harness/manual-client-private"),rows.getString(1));
    if(rows.next())throw new IllegalStateException("Exact manual fixture required");
   }
   Files.writeString(Path.of("../work/pr-c2-harness/manual-token-private"),f.manualToken());
   Path privateToken=Path.of("../work/pr-c2-harness/manual-token-private");Files.setPosixFilePermissions(privateToken,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
   Class.forName("com.shale.server.mcp.DcrConnectionServer").getMethod("start").invoke(null);
  } finally {f.unchangedHistoricalContracts();f.removeDatabase();f.removeIdentities();}
 }
}
