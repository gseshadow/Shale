package com.shale.data.security.ai;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.shale.core.runtime.DbSessionProvider;
public class ConnectionFixture extends AiCimdAuthorizationLocalSqlServerTest {
 public static ConnectionFixture CURRENT;
 public DataSource ds(String role){return source(role);}
 public DbSessionProvider scoped(String role,int t,int u){return provider(role,t,u);}
 public Connection open(String role,Integer t,Integer u)throws SQLException{return connection(role,t,u);}
 public Map<String,Object> bootstrapProperties(){return Map.of("shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://localhost:"+port+";databaseName="+db,"shale.ai.oauth.bootstrap.username",prefix+"_bootstrap","shale.ai.oauth.bootstrap.password",secrets.get("bootstrap"));}
 public String databaseName(){return db;}
 public String manualToken(){return token;}
 public AiCaseDataAttestation verify(long deadline){return attestation();}
 public static void main(String[] args)throws Exception {
  var f=CURRENT=new ConnectionFixture();
  try {
   f.identities();f.applicationIdentities();f.proofIdentities();f.oauthIdentities();f.reviewerIdentity();
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
    for(String batch:Files.readString(Path.of("../docs/sql/2026-10-14_pretenant_authentication_modules.sql")).split("(?im)^GO\\s*$"))if(!batch.isBlank())installSql(c,batch);
    sql(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ["+f.prefix+"_auth]; GRANT INSERT,UPDATE ON dbo.UserSessions TO ["+f.prefix+"_runtime]; GRANT SELECT ON dbo.ApplicationInstances TO ["+f.prefix+"_runtime]");
    String pw=Files.readString(Path.of("../work/pr-b-sql/browser-password"));
    String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(pw);
    try(var p=c.prepareStatement("UPDATE dbo.Users SET password_hash=? WHERE Id IN(71,72,81,82)")){p.setString(1,hash);p.executeUpdate();}
    sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1;UPDATE dbo.AiCimdProofControlV1 SET Enabled=1;UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1");
   }
   f.attestation().verify();
   Class.forName("com.shale.server.mcp.ConnectionServer").getMethod("start").invoke(null);
  } finally {f.unchangedHistoricalContracts();f.removeDatabase();f.removeIdentities();}
 }
}
