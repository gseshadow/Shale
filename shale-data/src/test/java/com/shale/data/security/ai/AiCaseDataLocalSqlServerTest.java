package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import com.shale.data.dao.CaseSummaryDao;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual distinct AI login over a hash-gated authoritative schema, synthetic rows only.
 * Never substitutes a synthetic table shape for the approved export. Skip is not acceptance. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiCaseDataLocalSqlServerTest {
    static final String ISSUER="https://shale.test/oauth/mcp",RESOURCE="https://shale.test/mcp",CALLBACK="https://client.test/callback",VERIFIER="v".repeat(43);
    String port,password,prefix,db;
    final Map<String,String> secrets=new LinkedHashMap<>();
    List<String> schema;
    String token,foreignToken;
    @BeforeAll void identities()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN"))&&System.getenv("SHALE_CASE_VERIFY_SCHEMA")!=null,"Hash-gated authoritative candidate and explicit disposable opt-in required");
        port=System.getenv("SHALE_AI_SQL_PORT");password=System.getenv("SHALE_AI_SQL_PASSWORD");
        assertNotNull(port);assertTrue(port.matches("[0-9]{4,5}")&&Integer.parseInt(port)>=1024&&Integer.parseInt(port)<=65535);assertNotNull(password);
        var safety=Class.forName("com.shale.data.verification.CaseReadVerificationSafety");var method=safety.getDeclaredMethod("schema",Path.class,String.class);method.setAccessible(true);
        @SuppressWarnings("unchecked") var imported=(List<String>)method.invoke(null,Path.of(System.getenv("SHALE_CASE_VERIFY_SCHEMA")),System.getenv("SHALE_CASE_VERIFY_SCHEMA_SHA256"));schema=imported;
        prefix="AiData_"+UUID.randomUUID().toString().replace("-","");
        try(var c=owner("master")) {
            assertTrue(Set.of(2,3,4).contains(count(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Disposable local SQL only; Azure forbidden");
            for(String role:List.of("ai","runtime","bootstrap","catalog")) {String secret=UUID.randomUUID()+"aB9!";sql(c,"CREATE LOGIN ["+prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");secrets.put(role,secret);if(role.equals("catalog"))sql(c,"GRANT VIEW ANY DEFINITION TO ["+prefix+"_catalog]");}
        }
    }
    @BeforeEach void database()throws Exception {
        db=prefix+"_"+UUID.randomUUID().toString().replace("-","");try(var c=owner("master")){sql(c,"CREATE DATABASE ["+db+"]");}
        try(var c=owner(db)) {
            for(String batch:schema)sql(c,batch);
            for(String migration:List.of("phase3b1.sql","phase3b2b.sql","phase3b2c1.sql","phase3b2c2.sql","revocation.sql","phase3b2d.sql","phase3b3.sql","phase3b7-confinement.sql"))sql(c,Files.readString(Path.of("../docs/sql/ai-integration/"+migration)));
            for(String role:secrets.keySet())sql(c,"CREATE USER ["+prefix+"_"+role+"] FOR LOGIN ["+prefix+"_"+role+"]");
            sql(c,"ALTER ROLE AiCaseDataRuntime ADD MEMBER ["+prefix+"_ai]; ALTER ROLE AiIntegrationRuntime ADD MEMBER ["+prefix+"_runtime]; ALTER ROLE AiIntegrationBootstrap ADD MEMBER ["+prefix+"_bootstrap]");
            sql(c,"ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER ["+prefix+"_catalog]");
            for(String table:List.of("Cases","Users","CaseUsers","CaseStatuses","Statuses","PracticeAreas","UserSessions","ShaleClients"))sql(c,"GRANT SELECT ON dbo."+table+" TO ["+prefix+"_runtime]");
            sql(c,"GRANT INSERT ON dbo.auditLog TO ["+prefix+"_runtime]");
            sql(c,"SET IDENTITY_INSERT dbo.ShaleClients ON; INSERT dbo.ShaleClients(Id,Code,DisplayName) VALUES(7,N'LOCAL_A',N'Local A'),(8,N'LOCAL_B',N'Local B'); SET IDENTITY_INSERT dbo.ShaleClients OFF");
            sql(c,"SET IDENTITY_INSERT dbo.Users ON; INSERT dbo.Users(id,ShaleClientId,is_admin,is_deleted,IsRemoved,name_first,name_last,email) VALUES(71,7,1,0,0,N'Synthetic',N'Admin',N'admin@example.invalid'),(72,7,0,0,0,N'Synthetic',N'A72',N'a72@example.invalid'),(73,7,0,0,0,N'Synthetic',N'A73',N'a73@example.invalid'),(81,8,1,0,0,N'Synthetic',N'BAdmin',N'badmin@example.invalid'),(82,8,0,0,0,N'Synthetic',N'B82',N'b82@example.invalid'); SET IDENTITY_INSERT dbo.Users OFF");
            sql(c,"INSERT dbo.AiCaseDataDeployment VALUES(1,N'"+ISSUER+"',N'"+RESOURCE+"','case-read-v1','"+ZoneId.systemDefault().getId()+"','UTC')");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1");
            insertCase(c,100,7,"Literal Alpha",false);insertCase(c,200,8,"Literal Alpha",false);insertCase(c,300,7,"Literal deleted",true);
        }
        controls(7,71).setTenantEnabled(true,null);controls(8,81).setTenantEnabled(true,null);
        token=issue(7,72);foreignToken=issue(8,82);
    }
    @AfterEach void removeDatabase()throws Exception {
        if(db!=null)try(var c=owner("master")) {
            // This exact nonce database is fixture-owned. Drain disconnecting/aborted leases before DDL.
            var sessions=new ArrayList<Integer>();
            try(var p=c.prepareStatement("SELECT session_id FROM sys.dm_exec_sessions WHERE database_id=DB_ID(?) AND is_user_process=1 AND session_id<>@@SPID")){p.setString(1,db);try(var r=p.executeQuery()){while(r.next())sessions.add(r.getInt(1));}}
            for(int session:sessions)sql(c,"KILL "+session);
            sql(c,"DROP DATABASE ["+db+"]");db=null;
        }
    }
    @AfterAll void removeIdentities()throws Exception {if(prefix!=null)try(var c=owner("master")){for(String role:secrets.keySet())sql(c,"DROP LOGIN ["+prefix+"_"+role+"]");}}

    @Test void migrationRepeatAndLiveAttestationRejectDrift()throws Exception {
        try(var c=owner(db)) {sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql")));sql(c,Files.readString(Path.of("src/main/resources/ai/confinement-catalog.sql")));}
        try(var c=source("catalog").getConnection()){sql(c,Files.readString(Path.of("src/main/resources/ai/confinement-catalog.sql")));}
        try(var c=source("catalog").getConnection();var st=c.createStatement();var r=st.executeQuery("SELECT CONVERT(varchar(64),CONVERT(varbinary(32),value),2) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseDataV1.Source'")) {
            assertTrue(r.next(),"Catalog verifier must see the current source receipt");var expected=new Properties();try(var in=Files.newInputStream(Path.of("src/main/resources/ai/confinement-modules.properties"))){expected.load(in);}
            assertEquals(expected.getProperty("Source").toLowerCase(Locale.ROOT),r.getString(1).toLowerCase(Locale.ROOT),"Compiled verifier and installed source receipt must match");assertFalse(r.next());
        }
        var attestation=attestation();attestation.verify();
        try(var c=owner(db)){sql(c,"GRANT SELECT ON dbo.Cases(Name) TO ["+prefix+"_ai]");}
        assertThrows(IllegalStateException.class,attestation::verify,"Column GRANT exceptions must remove readiness");
    }
    @Test void attestationRejectsIdentityRoleImpersonationOwnershipAndIndirectViewRoutes()throws Exception {
        assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(source("ai")),db,ISSUER,RESOURCE).verify(),"Same configured ordinary SID must fail");
        assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(),"wrong_database",ISSUER,RESOURCE).verify());
        for(String role:List.of("db_datareader","AiIntegrationRuntime","AiIntegrationBootstrap","AiCapabilityOperator")) {
            try(var c=owner(db)){sql(c,"ALTER ROLE "+role+" ADD MEMBER ["+prefix+"_ai]");}
            try{assertThrows(IllegalStateException.class,()->attestation().verify(),role);}finally{try(var c=owner(db)){sql(c,"ALTER ROLE "+role+" DROP MEMBER ["+prefix+"_ai]");}}
        }
        try(var c=owner("master")){sql(c,"GRANT IMPERSONATE ON LOGIN::["+prefix+"_runtime] TO ["+prefix+"_ai]");}
        try{assertThrows(IllegalStateException.class,()->attestation().verify(),"Specific LOGIN grants must not hide behind server-level inventory");}finally{try(var c=owner("master")){sql(c,"REVOKE IMPERSONATE ON LOGIN::["+prefix+"_runtime] FROM ["+prefix+"_ai]");}}
        try(var c=owner(db)){sql(c,"CREATE SCHEMA AiEscape AUTHORIZATION ["+prefix+"_ai]");}
        try{assertThrows(IllegalStateException.class,()->attestation().verify(),"Empty schema ownership is excess authority");}finally{try(var c=owner(db)){sql(c,"DROP SCHEMA AiEscape");}}
        try(var c=owner(db)){sql(c,"CREATE VIEW dbo.AiEscape AS SELECT Id,ShaleClientId,Name FROM dbo.Cases");sql(c,"GRANT SELECT ON dbo.AiEscape TO public");}
        try{assertThrows(IllegalStateException.class,()->attestation().verify(),"Public ownership-chain view must remove readiness");}finally{try(var c=owner(db)){sql(c,"DROP VIEW dbo.AiEscape");}}
        String original;try(var c=owner(db);var st=c.createStatement();var r=st.executeQuery("SELECT OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiDataAuthorizeCaseHandoffV1'))")){assertTrue(r.next());original=r.getString(1);}
        try(var c=owner(db)){sql(c,original.replaceFirst("CREATE PROCEDURE","ALTER PROCEDURE").replace("SET NOCOUNT ON;","SET NOCOUNT ON; -- unexpected drift\n"));}
        assertThrows(IllegalStateException.class,()->attestation().verify(),"Unexpected module definition must fail closed");
    }
    @Test void actualRestrictedLoginCannotEscapeBySelectingContextOrWritingForeignPhi()throws Exception {
        try(var c=connection("ai",null,null)) {
            assertEquals(0,count(c,"SELECT COALESCE(IS_MEMBER('ShaleAdmins'),0)+IS_MEMBER('db_owner')+IS_SRVROLEMEMBER('sysadmin')+IS_MEMBER('AiIntegrationRuntime')+IS_MEMBER('AiIntegrationBootstrap')+IS_MEMBER('AiCapabilityOperator')+IS_MEMBER('db_datareader')+IS_MEMBER('db_datawriter')"));
            for(String table:List.of("Cases","Users","CaseUsers","CaseStatuses","Statuses","PracticeAreas","auditLog","AiOAuthCredential","AiDelegatedSession","AiProductCapability","AiAuthorizationTransaction","AiCaseDataInvocation"))
                assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo."+table)).getErrorCode(),table);
            sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=82");
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT Name FROM dbo.Cases WHERE Id=200")).getErrorCode(),"#1919 context-switch statement itself must be denied");
            sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"INSERT dbo.auditLog(ShaleClientId,userId,objectTypeId,objectId,fieldName,fieldCode,stringValue) VALUES(8,82,1,200,'Case.Overview.Read',4,'action=READ;screen=Case.Overview')")).getErrorCode(),"#1919 foreign PHI INSERT must fail, independently of rollback");
            assertThrows(SQLException.class,()->sql(c,"EXECUTE AS USER='dbo'"));assertThrows(SQLException.class,()->sql(c,"ALTER ROLE db_datareader ADD MEMBER ["+prefix+"_ai]"));
            assertThrows(SQLException.class,()->sql(c,"ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=OFF)"));
            for(String helper:List.of("AiDataLocateAccessOwnerV1","AiDataLoadAuthorityV1","AiDataAppendAccessV1","AiDataAdmitV1"))
                assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo."+helper)).getErrorCode(),"Private helper execution itself must be denied: "+helper);
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT dbo.AiDataNormalizeV1(N'Literal')")).getErrorCode());
            for(String name:legacyProcedures()) {
                assertEquals(0,count(c,"SELECT COALESCE(HAS_PERMS_BY_NAME('dbo."+name+"','OBJECT','EXECUTE'),0)"),name);
                assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo."+name)).getErrorCode(),"Legacy EXEC itself must be denied before binding: "+name);
            }
            for(String table:List.of("IntegrationAccessAuditLog","IntegrationAccessAuditResource","AiCaseDataInvocation"))
                assertEquals(229,assertThrows(SQLException.class,()->sql(c,"INSERT dbo."+table+" DEFAULT VALUES")).getErrorCode(),table);
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT CredentialHash FROM dbo.AiOAuthCredential")).getErrorCode());
        }
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog"));}
    }
    @Test void validSearchOverviewAndProofBoundOneShotHandoffHaveRequiredAudits()throws Exception {
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);
            assertTrue(new AiCaseDataAccess(source("ai"),source("ai"),request).search(i,"Literal",0,b->0).size()>0);
            assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).search(i,"Literal",0,b->0));
        }
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            assertTrue(new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->0).size()>0);
        }
        try(var c=owner(db)) {
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read' AND ShaleClientId=7 AND UserId=72 AND ObjectId=100"));
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE Relation='RETURNED' AND CaseId=100"));
            assertEquals(11,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));
        }
    }
    @Test void incomingForeignContextNeverControlsProtectedReadsOrPhiOwnership()throws Exception {
        byte[] digest=AiOAuthCredentialStore.digest(token);
        try(var c=connection("ai",8,82)) {
            try(var request=authenticate(token)) {
                assertEquals(List.of(100),AiSql.call(c,"AiDataGetCaseOverviewV1",r->r.getInt("Id"),digest,request.authority().invocationId(),request.authority().correlationId(),100));
                assertEquals(8,count(c,"SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))"),"Incoming context must be restored, never trusted");
                AiSql.call(c,"AiDataAuthorizeCaseHandoffV1",r->true,digest,request.authority().invocationId(),request.authority().correlationId());
            }
            try(var request=authenticate(token)) {
                assertEquals(59104,assertThrows(SQLException.class,()->AiSql.call(c,"AiDataGetCaseOverviewV1",r->true,digest,request.authority().invocationId(),request.authority().correlationId(),200)).getErrorCode());
            }
        }finally{Arrays.fill(digest,(byte)0);}
        try(var c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE ShaleClientId=7 AND UserId=72 AND ObjectId=100"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE ShaleClientId=8 OR UserId=82 OR ObjectId=200"));}
    }
    @Test void malformedProofWrongInvocationAndForeignDigestFailBeforeCaseWork()throws Exception {
        assertThrows(SecurityException.class,()->authenticate(AiOAuthCredentialStore.opaque()),"Unknown well-formed digest must fail closed");
        byte[] digest=AiOAuthCredentialStore.digest(token),foreign=AiOAuthCredentialStore.digest(foreignToken);
        try(var request=authenticate(token);var c=connection("ai",8,82)) {
            for(int length:List.of(-1,0,31,33,64))for(String procedure:List.of("AiDataAuthenticateV1","AiDataSearchCasesV1","AiDataGetCaseOverviewV1","AiDataRecordCaseFailureV1","AiDataFinishCaseQuotaV1","AiDataAuthorizeCaseHandoffV1")) {
                String extra=switch(procedure){case "AiDataAuthenticateV1"->",@RequestDeadlineUtc=DATEADD(second,10,SYSUTCDATETIME())";case "AiDataSearchCasesV1"->",@NormalizedQuery=N'Literal',@Page=0";case "AiDataGetCaseOverviewV1"->",@CaseId=100";case "AiDataRecordCaseFailureV1"->",@Tool='search_cases',@Outcome='DENIED',@Reason='AUTHORITY'";default->"";};
                // All public digests are nontruncating varbinary(max), tested at the actual RPC boundary below.
                try(var p=c.prepareCall("{call dbo."+procedure+"("+String.join(",",Collections.nCopies(procedure.equals("AiDataAuthenticateV1")||procedure.equals("AiDataGetCaseOverviewV1")?4:procedure.equals("AiDataSearchCasesV1")?5:procedure.equals("AiDataRecordCaseFailureV1")?6:3,"?"))+")}")) {
                    if(length<0)p.setNull(1,Types.VARBINARY);else p.setBytes(1,new byte[length]);p.setObject(2,request.authority().invocationId());p.setObject(3,request.authority().correlationId());
                    switch(procedure){case "AiDataAuthenticateV1"->Utc(p,4,Instant.now().plusSeconds(5));case "AiDataSearchCasesV1"->{p.setString(4,"Literal");p.setInt(5,0);}case "AiDataGetCaseOverviewV1"->p.setInt(4,100);case "AiDataRecordCaseFailureV1"->{p.setString(4,"search_cases");p.setString(5,"DENIED");p.setString(6,"AUTHORITY");}}
                    assertEquals(59101,assertThrows(SQLException.class,p::execute).getErrorCode(),procedure+" length="+length);
                }
            }
            assertThrows(SQLException.class,()->AiSql.call(c,"AiDataSearchCasesV1",r->true,foreign,request.authority().invocationId(),request.authority().correlationId(),"Literal",0));
            assertThrows(SQLException.class,()->AiSql.call(c,"AiDataGetCaseOverviewV1",r->true,digest,UUID.randomUUID(),request.authority().correlationId(),100));
        } finally {Arrays.fill(digest,(byte)0);Arrays.fill(foreign,(byte)0);}
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog"));}
    }
    @Test void executableSearchParityCoversEveryPageProbeAndWindow()throws Exception {
        seedParity();
        for(int page=0;page<=9;page++)compareSearch("Parity ordered",page);
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);
            assertEquals(IntegrationAccessAudit.Outcome.THROTTLED,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).search(i,"Parity ordered",0,b->fail("Quota denial must precede encoding"))).outcome());
        }
        try(var c=owner(db)){assertEquals(100,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE Relation='RETURNED'"));assertEquals(100,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }
    @Test void executableSearchParityCoversNormalizationLiteralEscapingUnicodeAndPolicy()throws Exception {
        seedParity();
        for(String query:List.of("  Literal  ","literal %_[","résumé漢字","ΑΒΣ","İABC","𐐀ABC","quote';--ABC","Literal deleted","No match","Literal Alpha"))compareSearch(query,0);
    }
    @Test void executableOverviewParityCoversEightFieldsNullsUnassignedClosedAndLocalTimestamps()throws Exception {
        seedParity();var ordinary=new CaseSummaryDao(provider("runtime",7,72));
        for(long id:List.of(100L,400L,401L,402L))try(var request=authenticate(token)) {
            var expected=ordinary.readMinimizedCaseOverview(id,7,72).orElseThrow();
            var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            var actual=new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,id,b->0);
            var encoded=new java.io.ByteArrayOutputStream();new MinimizedCaseEncoder().encode(expected,i.correlationId(),encoded);assertArrayEquals(encoded.toByteArray(),bytes(actual),"Overview projection parity id="+id);
        }
        for(long id:List.of(200L,300L,999999L))try(var request=authenticate(token)) {
            assertTrue(ordinary.readMinimizedCaseOverview(id,7,72).isEmpty());
            var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            assertEquals(IntegrationAccessAudit.Reason.CASE_UNAVAILABLE,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,id,b->0)).reason());
        }
    }
    void compareSearch(String query,int page)throws Exception {
        var expected=new CaseSummaryDao(provider("runtime",7,72)).searchMinimizedCases(query,7,72,page,10);
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);
            var actual=new AiCaseDataAccess(source("ai"),source("ai"),request).search(i,query,page,b->0);
            var encoded=new java.io.ByteArrayOutputStream();new MinimizedCaseEncoder().encode(expected,i.correlationId(),encoded);
            assertArrayEquals(encoded.toByteArray(),bytes(actual),"Executable ordinary DAO/protected SQL parity query="+query+" page="+page);
        }
    }
    void seedParity()throws Exception {
        try(var c=owner(db)) {
            for(int n=0;n<102;n++)insertCase(c,1000+n,7,String.format(Locale.ROOT,"Parity ordered %03d",n/2),false);
            int n=400;for(String name:List.of("Literal %_[ résumé漢字","ΑΒΣ İABC 𐐀ABC","quote';--ABC"))insertCase(c,n++,7,name,false);
            sql(c,"SET IDENTITY_INSERT dbo.Roles ON; INSERT dbo.Roles(Id,Name,Description,IsActive,CreatedAt,UpdatedAt,ShaleClientId) VALUES(4,N'Synthetic Attorney',NULL,1,SYSUTCDATETIME(),SYSUTCDATETIME(),7),(11,N'Synthetic Assistant',NULL,1,SYSUTCDATETIME(),SYSUTCDATETIME(),7); SET IDENTITY_INSERT dbo.Roles OFF");
            sql(c,"INSERT dbo.PracticeAreas(ShaleClientId,Name,Color,SystemKey,IsActive,IsDeleted) VALUES(7,N'Synthetic Practice','#6C757D','parity',1,0); DECLARE @area int=SCOPE_IDENTITY(); UPDATE dbo.Cases SET PracticeAreaId=@area WHERE Id=400");
            sql(c,"INSERT dbo.Statuses(ShaleClientId,Name,Color,SortOrder,SystemKey,LifecycleKey,IsClosed,IsActive,IsDeleted) VALUES(7,N'Synthetic Closed','#6C757D',1,'parity','closed',1,1,0); DECLARE @status int=SCOPE_IDENTITY(); INSERT dbo.CaseStatuses(CaseId,StatusId,EffectiveDate,IsPrimary,CreatedAt,UpdatedAt) VALUES(400,@status,SYSUTCDATETIME(),1,SYSUTCDATETIME(),SYSUTCDATETIME())");
            sql(c,"INSERT dbo.CaseUsers(ShaleClientId,CaseId,UserId,RoleId,IsPrimary,CreatedAt,UpdatedAt) VALUES(7,400,72,4,1,SYSUTCDATETIME(),SYSUTCDATETIME()),(7,400,73,11,1,SYSUTCDATETIME(),SYSUTCDATETIME())");
            sql(c,"UPDATE dbo.Cases SET CaseNumber=NULL WHERE Id=401; UPDATE dbo.Cases SET Name=NULL WHERE Id=402");
        }
    }
    @Test void normalizationMatchesJavaReferenceIncludingContextualSigmaAndSupplementaryText()throws Exception {
        try(var c=owner(db);var p=c.prepareStatement("SELECT dbo.AiDataNormalizeV1(?)")) {
            for(String literal:List.of("Literal","ΑΒΣ","ΑΒΣΑ","ΑΒΣ'Α","ΑΒΣ Α","İABC","𐐀ABC","éABC","ABC %_[","quote';--ABC","漢字ABC","ABC\u2003")) {
                p.setString(1,literal);try(var r=p.executeQuery()){assertTrue(r.next());String expected=literal.equals(literal.strip())?"%"+literal.toLowerCase(Locale.ROOT).replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%":null;assertEquals(expected,r.getString(1),literal);}
            }
        }
    }
    @Test void revokedAndExpiredProofHaveOnlyHistoricalFailureOwnershipAndCannotRead()throws Exception {
        String sameTenantOther=issue(7,73);byte[] other=AiOAuthCredentialStore.digest(sameTenantOther);
        try(var request=authenticate(token);var c=connection("ai",8,82)) {
            var id=request.authority().invocationId();var correlation=request.authority().correlationId();
            assertThrows(SQLException.class,()->AiSql.call(c,"AiDataSearchCasesV1",r->true,other,id,correlation,"Literal",0));
            byte[] digest=AiOAuthCredentialStore.digest(token);
            try {
                try(var owner=owner(db)){sql(owner,"UPDATE dbo.AiOAuthCredential SET RevokedAt=SYSUTCDATETIME() WHERE Id="+request.authority().credentialId());}
                assertThrows(SecurityException.class,()->authenticate(token));
                assertEquals(59101,assertThrows(SQLException.class,()->AiSql.call(c,"AiDataGetCaseOverviewV1",r->true,digest,id,correlation,100)).getErrorCode());
                for(String outcome:List.of("ALLOWED_READ","ALLOWED_READ ","arbitrary"))assertEquals(59102,assertThrows(SQLException.class,()->AiSql.call(c,"AiDataRecordCaseFailureV1",r->true,digest,id,correlation,"get_case_overview",outcome,"AUTHORITY")).getErrorCode());
                for(int repeat=0;repeat<2;repeat++) {
                    AiSql.call(c,"AiDataRecordCaseFailureV1",r->true,digest,id,correlation,"get_case_overview","DENIED","AUTHORITY",AiSql.nullable(null,Types.INTEGER),200L);
                    AiSql.call(c,"AiDataFinishCaseQuotaV1",r->true,digest,id,correlation);
                }
                assertThrows(SQLException.class,()->AiSql.call(c,"AiDataFinishCaseQuotaV1",r->true,other,id,correlation));
            }finally{Arrays.fill(digest,(byte)0);}
        }finally{Arrays.fill(other,(byte)0);}
        String expired=AiOAuthCredentialStore.opaque();byte[] expiredDigest=AiOAuthCredentialStore.digest(expired),liveDigest=AiOAuthCredentialStore.digest(sameTenantOther);
        try(var c=owner(db);var p=c.prepareStatement("INSERT dbo.AiOAuthCredential(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt) SELECT ShaleClientId,IntegrationId,UserId,GrantId,SessionId,?,'ACCESS',ScopeMask,Generation,DATEADD(minute,-4,SYSUTCDATETIME()),DATEADD(minute,-1,SYSUTCDATETIME()) FROM dbo.AiOAuthCredential WHERE CredentialHash=?")) {
            p.setBytes(1,expiredDigest);p.setBytes(2,liveDigest);assertEquals(1,p.executeUpdate());
        }finally{Arrays.fill(expiredDigest,(byte)0);Arrays.fill(liveDigest,(byte)0);}
        assertThrows(SecurityException.class,()->authenticate(expired));
        try(var c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='DENIED'"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog"));assertEquals(0,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }
    @Test void suppressedPhiAndFailedParentOrChildRollBackEverySuccessFactWithoutRefund()throws Exception {
        for(String target:List.of("auditLog","IntegrationAccessAuditLog","IntegrationAccessAuditResource")) {
            try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.ConfinementAuditFault ON dbo."+target+(target.equals("auditLog")?" INSTEAD OF INSERT AS BEGIN SET NOCOUNT ON; RETURN; END":" AFTER INSERT AS BEGIN SET NOCOUNT ON; THROW 59999,'Synthetic audit fault.',1; END"));}
            try(var request=authenticate(token)) {
                var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
                assertEquals(IntegrationAccessAudit.Reason.AUDIT,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->fail("No data may reach encoding"))).reason(),target);
            }finally{try(var c=owner(db)){sql(c,"DROP TRIGGER dbo.ConfinementAuditFault");}}
        }
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));assertEquals(3,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE ReleasedAt IS NULL"));}
    }
    @Test void physicalAiPoolReuseDerivesA1A2B1A1AndEvictsReadOnlyPoison()throws Exception {
        String a2=issue(7,73);var config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(source("ai"));config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(2000);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config)) {
            int spid=-1;
            for(String proof:List.of(token,a2,foreignToken,token)) {
                try(var c=pool.getConnection()){int actual=count(c,"SELECT @@SPID");if(spid<0)spid=actual;else assertEquals(spid,actual,"Healthy physical SQL session must actually be reused");}
                try(var request=AiCaseDataAccess.authenticate(pool,proof,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L)) {
                    int expected=proof.equals(foreignToken)?200:100;
                    var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
                    assertTrue(new AiCaseDataAccess(pool,pool,request).overview(i,(long)expected,b->0).size()>0);
                }
                try(var c=pool.getConnection()){assertEquals(0,count(c,"SELECT CASE WHEN SESSION_CONTEXT(N'ShaleClientId') IS NULL AND SESSION_CONTEXT(N'PrincipalUserId') IS NULL THEN 0 ELSE 1 END"));}
            }
            UUID poisoned;
            try(var c=pool.getConnection()){poisoned=c.unwrap(com.microsoft.sqlserver.jdbc.SQLServerConnection.class).getClientConnectionId();sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8,@read_only=1");}
            assertThrows(SecurityException.class,()->AiCaseDataAccess.authenticate(pool,token,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L));
            try(var request=AiCaseDataAccess.authenticate(pool,token,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L);var c=pool.getConnection()){assertNotEquals(poisoned,c.unwrap(com.microsoft.sqlserver.jdbc.SQLServerConnection.class).getClientConnectionId(),"Poisoned physical connection must be discarded; SQL can reuse a SPID");}
        }
    }
    @Test void phiTimezoneConversionMatchesJavaAcrossDstBoundaries()throws Exception {
        try(var c=owner(db);var p=c.prepareStatement("SELECT CONVERT(datetime2(7),(CONVERT(datetime2(7),?) AT TIME ZONE 'UTC') AT TIME ZONE ?)")) {
            for(var pair:Map.of("America/New_York","Eastern Standard Time","Europe/London","GMT Standard Time","Australia/Sydney","AUS Eastern Standard Time").entrySet()) {
                ZoneId zone=ZoneId.of(pair.getKey());Instant cursor=Instant.parse("2025-01-01T00:00:00Z"),end=Instant.parse("2029-01-01T00:00:00Z");
                while(true) {var transition=zone.getRules().nextTransition(cursor);if(transition==null||!transition.getInstant().isBefore(end))break;
                    for(Instant sample:List.of(transition.getInstant().minusSeconds(1),transition.getInstant(),transition.getInstant().plusSeconds(1))) {Utc(p,1,sample);p.setString(2,pair.getValue());try(var r=p.executeQuery()){assertTrue(r.next());assertEquals(LocalDateTime.ofInstant(sample,zone),r.getTimestamp(1).toLocalDateTime(),pair.getKey()+" DST boundary");}}
                    cursor=transition.getInstant().plusSeconds(1);
                }
            }
        }
    }
    @Test void finalHandoffWaitsAcrossDatabaseExpiryAndDeniesWithoutProtectedOutput()throws Exception {
        try(var request=authenticate(token)) {
            var observed=new java.util.concurrent.CompletableFuture<Void>();
            var hook=new java.util.concurrent.atomic.AtomicBoolean();
            String event="AiDataExpiry_"+UUID.randomUUID().toString().replace("-","");
            var released=new java.util.concurrent.atomic.AtomicReference<Instant>();
            DataSource racing=(DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},(proxy,method,args)-> {
                Object result=invoke(source("ai"),method,args);
                if(!method.getName().equals("getConnection"))return result;
                var c=(Connection)result;
                return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)-> {
                    Object value=invoke(c,m,a);
                    if(m.getName().equals("prepareCall")&&a[0].toString().contains("AiDataAuthorizeCaseHandoffV1")&&hook.compareAndSet(false,true)) {
                        int spid=count(c,"SELECT @@SPID");
                        Connection held=owner(db);int blocker=count(held,"SELECT @@SPID");
                        sql(held,"SET IMPLICIT_TRANSACTIONS OFF; BEGIN TRANSACTION; UPDATE dbo.AiDelegatedSession SET IdleExpiresAt=DATEADD(second,1,SYSUTCDATETIME()) WHERE SessionId='"+request.authority().delegatedSessionId()+"'; SELECT 1 FROM dbo.AiDelegatedSession WITH(TABLOCKX,HOLDLOCK)");
                        assertEquals(1,count(held,"SELECT @@TRANCOUNT"));
                        try(var observer=owner(db)) {
                            // Statement-completion text only: no RPC/parameter capture or sql_text action.
                            sql(observer,"CREATE EVENT SESSION ["+event+"] ON SERVER ADD EVENT sqlserver.sp_statement_completed(ACTION(sqlserver.session_id) WHERE ([sqlserver].[session_id]="+spid+")) ADD TARGET package0.ring_buffer WITH(MAX_MEMORY=1024 KB,EVENT_RETENTION_MODE=NO_EVENT_LOSS)");
                            sql(observer,"ALTER EVENT SESSION ["+event+"] ON SERVER STATE=START");
                        }
                        new Thread(()-> {
                            try(held;var observer=owner(db)) {
                                long until=System.nanoTime()+2_500_000_000L;boolean waiting=false;
                                while(System.nanoTime()<until) {
                                    if(count(observer,"SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id="+spid+" AND blocking_session_id="+blocker+" AND wait_type LIKE 'LCK_M_%'")>0) {waiting=true;break;}
                                    Thread.sleep(10);
                                }
                                assertTrue(waiting,"Exact final-gate SPID must positively wait on the required SQL lock");
                                boolean expired=false;
                                while(System.nanoTime()<until) {
                                    if(count(held,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE SessionId='"+request.authority().delegatedSessionId()+"' AND IdleExpiresAt<=SYSUTCDATETIME()")>0){expired=true;break;}
                                    Thread.sleep(10);
                                }
                                assertTrue(expired,"Database UTC must cross the persisted exact idle deadline before unlock");
                                try(var st=held.createStatement();var r=st.executeQuery("SELECT SYSUTCDATETIME() Sample")){r.next();released.set(com.shale.data.util.UtcJdbcTimestamp.get(r,"Sample").toInstant());}
                                sql(held,"COMMIT TRANSACTION");observed.complete(null);
                            }catch(Throwable failure){observed.completeExceptionally(failure);}
                        },"ai-data-expiry-observer").start();
                    }
                    return value;
                });
            });
            try {
                var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
                var denied=assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(racing,source("ai"),request).overview(invocation,100L,b->0));
                assertEquals(IntegrationAccessAudit.Outcome.DENIED,denied.outcome());assertEquals(IntegrationAccessAudit.Reason.AUTHORITY,denied.reason());
                observed.get(5,java.util.concurrent.TimeUnit.SECONDS);assertTrue(hook.get());assertNotNull(released.get());
                try(var c=owner(db)) {
                    assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE ObjectId=100"));
                    assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));
                    assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Stage='TERMINAL' AND Outcome='DENIED'"));
                    try(var st=c.createStatement();var r=st.executeQuery("SELECT CONVERT(nvarchar(max),t.target_data) FROM sys.dm_xe_sessions s JOIN sys.dm_xe_session_targets t ON t.event_session_address=s.address WHERE s.name='"+event+"'")) {
                        assertTrue(r.next());String xml=r.getString(1);
                        var document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var events=document.getElementsByTagName("event");boolean sample=false;
                        for(int n=0;n<events.getLength();n++) {
                            var e=(org.w3c.dom.Element)events.item(n);String text=e.getTextContent();
                            if(text.contains("DECLARE @now datetime2(7)=SYSUTCDATETIME()")&&Instant.parse(e.getAttribute("timestamp")).compareTo(released.get())>=0)sample=true;
                        }
                        assertTrue(sample,"The same final gate must sample DB UTC after the positively observed lock release");
                    }
                }
            } finally {
                if(hook.get())try(var c=owner(db)){sql(c,"ALTER EVENT SESSION ["+event+"] ON SERVER STATE=STOP; DROP EVENT SESSION ["+event+"] ON SERVER");}
            }
        }
    }
    @Test void authoritativeSourceMaximumCannotReachOverviewByteCeilingsAndOverlengthStillFails()throws Exception {
        seedParity();boundaryRelationships();
        try(var c=owner(db);var p=c.prepareStatement("UPDATE dbo.Cases SET Name=?,CaseNumber=? WHERE Id=400")) {p.setString(1,"\u0001".repeat(255));p.setString(2,"\u0001".repeat(100));p.executeUpdate();}
        int maximum;
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            var result=new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,400L,b->0);maximum=result.size();
            // Reserve 64 bytes for maximal integer IDs and timestamp text. The entire
            // request body is capped at 8192 bytes, conservatively bounding its echoed ID.
            assertTrue(maximum+64<8192,"Valid authoritative source widths cannot reach 8 KiB");
            assertTrue(3L*(maximum+64)+1024+8192<32768,
                    "Duplicate text, fixed envelope, maximal scalar widths and request ID fit below 32 KiB");
        }
        try(var c=owner(db);var p=c.prepareStatement("UPDATE dbo.Cases SET Name=? WHERE Id=400")){p.setString(1,"\u0001".repeat(256));p.executeUpdate();}
        try(var request=authenticate(token)) {
            var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            assertEquals(IntegrationAccessAudit.Reason.OVERSIZED,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,400L,b->fail("Overlength source must never reach encoding"))).reason());
        }
        Files.createDirectories(Path.of("../work/phase3b7"));Files.writeString(Path.of("../work/phase3b7/overview-source-maximum.json"),"{\"maximumStructuredBytes\":"+maximum+",\"conservativeEnvelopeUpperBound\":"+(3L*(maximum+64)+1024+8192)+",\"caseNumberUnits\":100,\"caseNameUnits\":255,\"statusUnits\":50,\"practiceAreaUnits\":150,\"userDisplayUnits\":101,\"structured8191Through8193Reachable\":false,\"full32767Through32769Reachable\":false}\n");
    }
    @Test void exactSearchStructuredBoundariesUseValidAuthoritativeSourceAndRetainCommittedHistory()throws Exception {
        seedParity();boundaryRelationships();
        try(var c=owner(db)) {
            for(int n=0;n<10;n++) {
                insertCase(c,500+n,7,"BND"+"a".repeat(252),false);
                sql(c,"UPDATE dbo.Cases SET CaseNumber=REPLICATE(N'a',100),PracticeAreaId=(SELECT PracticeAreaId FROM dbo.Cases WHERE Id=400) WHERE Id="+(500+n));
                sql(c,"INSERT dbo.CaseStatuses(CaseId,StatusId,EffectiveDate,IsPrimary,CreatedAt,UpdatedAt) SELECT "+(500+n)+",StatusId,EffectiveDate,IsPrimary,CreatedAt,UpdatedAt FROM dbo.CaseStatuses WHERE CaseId=400");
                sql(c,"INSERT dbo.CaseUsers(ShaleClientId,CaseId,UserId,RoleId,IsPrimary,CreatedAt,UpdatedAt) SELECT ShaleClientId,"+(500+n)+",UserId,RoleId,IsPrimary,CreatedAt,UpdatedAt FROM dbo.CaseUsers WHERE CaseId=400");
            }
        }
        var dao=new CaseSummaryDao(provider("runtime",7,72));
        var base=new java.io.ByteArrayOutputStream();new MinimizedCaseEncoder().encode(dao.searchMinimizedCases("BND",7,72,0,10),new UUID(0,0),base);
        int initial=base.size();assertTrue(initial<32767&&initial+5*3520>32769,"Real source shape must permit exact search boundaries");
        for(int target:List.of(32767,32768,32769)) {
            int extra=target-initial,controls=extra/5,unicode=extra%5,position=0;
            try(var c=owner(db);var p=c.prepareStatement("UPDATE dbo.Cases SET Name=?,CaseNumber=? WHERE Id=?")) {
                for(int n=0;n<10;n++) {
                    var name=new StringBuilder("BND");var number=new StringBuilder();
                    for(int k=0;k<352;k++,position++) {char ch=position<controls?'\u0001':position<controls+unicode?'é':'a';if(k<252)name.append(ch);else number.append(ch);}
                    p.setString(1,name.toString());p.setString(2,number.toString());p.setInt(3,500+n);p.executeUpdate();
                }
            }
            // Fixture-only ordinary summary measurement; never an automatic reread of a failed protected call.
            var measured=new java.io.ByteArrayOutputStream();new MinimizedCaseEncoder().encode(dao.searchMinimizedCases("BND",7,72,0,10),new UUID(0,0),measured);assertEquals(target,measured.size());
            try(var request=authenticate(token)) {
                var i=IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);
                var access=new AiCaseDataAccess(source("ai"),source("ai"),request);
                if(target<=32768)assertEquals(target,access.search(i,"BND",0,b->0).size());
                else assertEquals(IntegrationAccessAudit.Reason.OVERSIZED,assertThrows(IntegrationReadException.class,()->access.search(i,"BND",0,b->fail("No partial encoded buffer may escape"))).reason());
            }
        }
        try(var c=owner(db)){assertEquals(3,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));assertEquals(30,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }
    @Test void exactFullResponseMeasurementCeilingsWithActualProtectedRpcAndNoReplay()throws Exception {
        for(boolean overview:List.of(false,true))for(int delta:List.of(-1,0,1)) {
            int maximum=overview?32768:65536;
            try(var request=authenticate(token)) {
                var access=new AiCaseDataAccess(source("ai"),source("ai"),request);
                var i=overview?IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L):IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);
                java.util.function.Supplier<DisclosureBuffer> call=()->overview?access.overview(i,100L,b->maximum+delta):access.search(i,"Literal",0,b->maximum+delta);
                if(delta<=0)assertTrue(call.get().size()>0);else assertEquals(IntegrationAccessAudit.Reason.OVERSIZED,assertThrows(IntegrationReadException.class,call::get).reason());
            }
        }
        try(var c=owner(db)){assertEquals(6,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));assertEquals(3,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'"));assertEquals(33,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }
    @Test void authoritativeAdminReviewIsTenantBoundPagedAndRetainsHistoricalCaseIds()throws Exception {
        try(var request=authenticate(token)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->0);}
        var review=new IntegrationAccessReview(provider("runtime",7,71));var rows=review.page(0,100);
        assertEquals(1,rows.size());assertEquals(100,rows.getFirst().caseId());
        assertThrows(IllegalStateException.class,()->new IntegrationAccessReview(provider("runtime",7,72)).page(0,100));
        assertTrue(new IntegrationAccessReview(provider("runtime",8,81)).page(0,100).isEmpty(),"Foreign admin must not receive A's history");
        assertThrows(IllegalArgumentException.class,()->review.page(0,101));
        try(var c=owner(db)){sql(c,"DELETE dbo.Cases WHERE Id=100");}
        assertEquals(100,review.page(0,100).getFirst().caseId(),"Historical resource IDs deliberately survive Case deletion without a Case FK/dump");
    }
    @Test void concurrentDedicatedPoolRequestsAndFailedReuseKeepAuthorityAndAuditSeparated()throws Exception {
        var config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(source("ai"));config.setMaximumPoolSize(2);config.setMinimumIdle(0);config.setConnectionTimeout(2000);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config);var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var barrier=new java.util.concurrent.CyclicBarrier(2);var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(String proof:List.of(token,foreignToken))tasks.add(executor.submit(()->{
                try(var request=AiCaseDataAccess.authenticate(pool,proof,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L)) {
                    barrier.await(3,java.util.concurrent.TimeUnit.SECONDS);long id=request.authority().shaleClientId()==7?100:200;
                    var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
                    return new AiCaseDataAccess(pool,pool,request).overview(i,id,b->0).size();
                }
            }));
            for(var task:tasks)assertTrue(task.get(8,java.util.concurrent.TimeUnit.SECONDS)>0);
            try(var request=AiCaseDataAccess.authenticate(pool,token,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L)) {
                var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
                assertEquals(IntegrationAccessAudit.Reason.CASE_UNAVAILABLE,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(pool,pool,request).overview(i,200L,b->fail("Foreign data must never be encoded"))).reason());
            }
            for(String proof:List.of(foreignToken,token))try(var request=AiCaseDataAccess.authenticate(pool,proof,ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L)) {
                var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);assertTrue(new AiCaseDataAccess(pool,pool,request).overview(i,proof.equals(token)?100L:200L,b->0).size()>0);
            }
            try(var lease=pool.getConnection()){assertEquals(0,count(lease,"SELECT CASE WHEN SESSION_CONTEXT(N'ShaleClientId') IS NULL AND SESSION_CONTEXT(N'PrincipalUserId') IS NULL THEN 0 ELSE 1 END"));}
        }
        try(var c=owner(db)) {
            assertEquals(4,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r JOIN dbo.IntegrationAccessAuditLog a ON a.Id=r.EventId WHERE r.Relation='RETURNED' AND ((a.ShaleClientId=7 AND r.CaseId<>100) OR (a.ShaleClientId=8 AND r.CaseId<>200))"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE (ShaleClientId=7 AND (UserId<>72 OR ObjectId<>100)) OR (ShaleClientId=8 AND (UserId<>82 OR ObjectId<>200))"));
        }
    }
    @Test void protectedQuotaSurvivesReplacementJvmAndNewAccessGrantWithoutReplayOrConcurrentOverspend()throws Exception {
        try(var request=authenticate(token)) {var i=IntegrationInvocation.search(request.authority(),System.nanoTime()+9_000_000_000L);new AiCaseDataAccess(source("ai"),source("ai"),request).search(i,"Literal",0,b->0);}
        replacementProbe(false);
        try(var c=owner(db)){assertEquals(11,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
        // Owner-only past-accounting fixture reaches exactly 200 durable user rows without resetting history.
        try(var request=authenticate(token);var c=owner(db);var p=c.prepareStatement("INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt,ReleasedAt) VALUES(7,?,72,?,?,NEWID(),?,?,1,DATEADD(hour,-1,SYSUTCDATETIME()),DATEADD(second,-3590,SYSUTCDATETIME()),DATEADD(second,-3595,SYSUTCDATETIME()))")) {
            p.setObject(1,request.authority().integrationId());p.setObject(2,request.authority().grantId());p.setObject(3,request.authority().delegatedSessionId());
            for(int n=0;n<27;n++){p.setString(4,n<18?"SEARCH":"OVERVIEW");p.setInt(5,n<18?10:1);p.executeUpdate();}
        }
        replacementProbe(true);
        String newAccessAndGrant=issue(7,72);
        try(var request=authenticate(newAccessAndGrant)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);assertEquals(IntegrationAccessAudit.Outcome.THROTTLED,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->fail("New credentials must not reset user accounting"))).outcome());}
        try(var c=owner(db)){assertEquals(200,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation WHERE ShaleClientId=7 AND UserId=72"));}
        // Two new service instances share one user lease; private encoded output waits before handoff.
        String other=issue(7,73);var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->{try(var request=authenticate(other)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);return new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->{entered.countDown();try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return 0;}).size();}});
            try {
                assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
                try(var request=authenticate(other)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);assertEquals(IntegrationAccessAudit.Outcome.THROTTLED,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->fail("Distributed concurrency may not overspend"))).outcome());}
            }finally{release.countDown();}
            assertTrue(first.get(5,java.util.concurrent.TimeUnit.SECONDS)>0);
        }
        try(var request=authenticate(other);var c=connection("ai",null,null)) {
            byte[] digest=AiOAuthCredentialStore.digest(other);var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);
            try {new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->0);assertThrows(SQLException.class,()->AiSql.call(c,"AiDataGetCaseOverviewV1",r->true,digest,i.invocationId(),i.correlationId(),100));assertThrows(SQLException.class,()->AiSql.call(c,"AiDataAuthorizeCaseHandoffV1",r->true,digest,i.invocationId(),i.correlationId()));}
            finally{Arrays.fill(digest,(byte)0);}
        }
    }
    private void replacementProbe(boolean denied)throws Exception {
        var command=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("surefire.test.class.path"),ConfinedRestartProbe.class.getName(),Boolean.toString(denied));
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().put("AI_PROBE_URL",url(db));builder.environment().put("AI_PROBE_USER",prefix+"_ai");builder.environment().put("AI_PROBE_PASSWORD",secrets.get("ai"));builder.environment().put("AI_PROBE_ACCESS",token);
        var process=builder.start();try{assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Replacement JVM must observe persisted protected accounting");}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
    @Test void protectedCallRateSurvivesReplacementJvmAndFreshGrant()throws Exception {
        for(int n=0;n<10;n++)try(var request=authenticate(token)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->0);}
        replacementProbe(true);
        String fresh=issue(7,72);assertProtectedThrottle(fresh);
        try(var c=owner(db)){assertEquals(10,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));assertEquals(10,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));assertEquals(12,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation"));}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"rows","rate","concurrency"})
    void protectedAggregateQuotaCeilingsApplyAcrossUsersIntegrationsAndInstances(String dimension)throws Exception {
        try(var c=owner(db)){sql(c,"SET IDENTITY_INSERT dbo.Users ON; INSERT dbo.Users(id,ShaleClientId,is_admin,is_deleted,IsRemoved,name_first,email) VALUES(74,7,0,0,0,N'Quota74',N'quota74@example.invalid'),(75,7,0,0,0,N'Quota75',N'quota75@example.invalid'),(76,7,0,0,0,N'Quota76',N'quota76@example.invalid'),(77,7,0,0,0,N'Quota77',N'quota77@example.invalid'),(78,7,0,0,0,N'Quota78',N'quota78@example.invalid'); SET IDENTITY_INSERT dbo.Users OFF");}
        AiIntegrationSecurityService.Registration first;try(var request=authenticate(token)){first=controls(7,71).inspectRegistrations().stream().filter(r->r.integrationId().equals(request.authority().integrationId())).findFirst().orElseThrow();}
        String a73=issueWithRegistration(7,73,first),a74=issueWithRegistration(7,74,first);
        int age=dimension.equals("rows")?3600:5;boolean active=dimension.equals("concurrency");if(active)age=0;
        seedProtectedQuota(a73,dimension.equals("rows")?20:dimension.equals("rate")?10:1,dimension.equals("rows")?10:1,age,active);
        seedProtectedQuota(a74,dimension.equals("rows")?20:dimension.equals("rate")?10:1,dimension.equals("rows")?10:1,age,active);
        if(dimension.equals("rows"))seedProtectedQuota(issueWithRegistration(7,75,first),10,10,age,false);
        assertProtectedThrottle(token); // 500 integration rows / 20 calls / 2 active leases; user 72 itself has none.
        var second=controls(7,71).register("Aggregate second",false,AiScope.fromMask(3),1,List.of(URI.create(CALLBACK)));
        var third=controls(7,71).register("Aggregate third",false,AiScope.fromMask(3),1,List.of(URI.create(CALLBACK)));
        if(dimension.equals("rows")) {
            seedProtectedQuota(issueWithRegistration(7,76,second),20,10,age,false);seedProtectedQuota(issueWithRegistration(7,77,second),20,10,age,false);seedProtectedQuota(issueWithRegistration(7,78,third),10,10,age,false);
        } else if(dimension.equals("rate")) {
            seedProtectedQuota(issueWithRegistration(7,75,second),10,1,age,false);seedProtectedQuota(issueWithRegistration(7,76,second),9,1,age,false);seedProtectedQuota(issueWithRegistration(7,77,third),10,1,age,false);seedProtectedQuota(issueWithRegistration(7,78,third),10,1,age,false);
        } else {
            seedProtectedQuota(issueWithRegistration(7,75,second),1,1,0,true);seedProtectedQuota(issueWithRegistration(7,76,second),1,1,0,true);seedProtectedQuota(issueWithRegistration(7,77,third),1,1,0,true);
        }
        var fourth=controls(7,71).register("Aggregate fresh",false,AiScope.fromMask(3),1,List.of(URI.create(CALLBACK)));
        String fresh=issueWithRegistration(7,72,fourth);assertProtectedThrottle(fresh); // tenant 1000 rows / 60 calls / 5 active leases.
        try(var c=owner(db)) {
            if(dimension.equals("rows"))assertEquals(1000,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));
            if(dimension.equals("rate"))assertEquals(61,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE AdmittedAt>DATEADD(second,-60,SYSUTCDATETIME())"));
            if(dimension.equals("concurrency"))assertEquals(5,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>SYSUTCDATETIME()"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));
        }
    }
    private void assertProtectedThrottle(String proof) {
        try(var request=authenticate(proof)){var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+9_000_000_000L);assertEquals(IntegrationAccessAudit.Outcome.THROTTLED,assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(source("ai"),source("ai"),request).overview(i,100L,b->fail("Durable aggregate budget must withhold output"))).outcome());}
    }
    private void seedProtectedQuota(String proof,int rows,int cost,int ageSeconds,boolean active)throws Exception {
        try(var request=authenticate(proof);var c=owner(db);var p=c.prepareStatement("DECLARE @at datetime2(7)=DATEADD(second,?,SYSUTCDATETIME()); INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt,ReleasedAt) VALUES(?,?,?,?,?,NEWID(),?,?,1,@at,DATEADD(second,15,@at),CASE WHEN ?=1 THEN NULL ELSE DATEADD(second,1,@at) END)")) {
            p.setInt(1,-ageSeconds);p.setInt(2,request.authority().shaleClientId());p.setObject(3,request.authority().integrationId());p.setInt(4,request.authority().actingUserId());p.setObject(5,request.authority().grantId());p.setObject(6,request.authority().delegatedSessionId());p.setString(7,cost==10?"SEARCH":"OVERVIEW");p.setInt(8,cost);p.setBoolean(9,active);
            for(int n=0;n<rows;n++)p.executeUpdate();
        }
    }
    public static final class ConfinedRestartProbe {
        public static void main(String[] args)throws Exception {
            var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(System.getenv("AI_PROBE_URL"));source.setUser(System.getenv("AI_PROBE_USER"));source.setPassword(System.getenv("AI_PROBE_PASSWORD"));
            try(var request=AiCaseDataAccess.authenticate(source,System.getenv("AI_PROBE_ACCESS"),ISSUER,RESOURCE,System.nanoTime()+9_000_000_000L)) {
                var i=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);var access=new AiCaseDataAccess(source,source,request);
                if(Boolean.parseBoolean(args[0]))assertEquals(IntegrationAccessAudit.Outcome.THROTTLED,assertThrows(IntegrationReadException.class,()->access.overview(i,100L,b->0)).outcome());else assertTrue(access.overview(i,100L,b->0).size()>0);
            }
        }
    }
    private void boundaryRelationships()throws Exception {
        try(var c=owner(db)) {
            sql(c,"UPDATE dbo.Statuses SET Name=REPLICATE(NCHAR(1),50) WHERE SystemKey='parity'; UPDATE dbo.PracticeAreas SET Name=REPLICATE(NCHAR(1),150) WHERE SystemKey='parity'");
            sql(c,"UPDATE dbo.Users SET name_first=REPLICATE(NCHAR(1),50),name_last=REPLICATE(NCHAR(1),50) WHERE ShaleClientId=7 AND Id IN(72,73)");
        }
    }
    private static Object invoke(Object target,java.lang.reflect.Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
    }
    AiCaseDataAttestation attestation(){return new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(source("runtime"),source("bootstrap")),db,ISSUER,RESOURCE);}
    AiCaseDataRequest authenticate(String token){return AiCaseDataAccess.authenticate(source("ai"),token,ISSUER,RESOURCE,System.nanoTime()+10_000_000_000L);}
    AiIntegrationSecurityService controls(int tenant,int user){return new AiIntegrationSecurityService(provider("runtime",tenant,user),true,ISSUER,RESOURCE);}
    String issue(int tenant,int user)throws Exception {
        var registration=controls(tenant,tenant==7?71:81).register("Synthetic confinement",false,AiScope.fromMask(3),1,List.of(URI.create(CALLBACK)));
        return issueWithRegistration(tenant,user,registration);
    }
    String issueWithRegistration(int tenant,int user,AiIntegrationSecurityService.Registration registration)throws Exception {
        UUID web=UUID.randomUUID(),tx=UUID.randomUUID();String code=AiOAuthCredentialStore.opaque(),nonce=AiOAuthCredentialStore.opaque();
        try(var c=owner(db);var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,?,?,'WEB',?,SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){p.setObject(1,web);p.setInt(2,tenant);p.setInt(3,user);p.setObject(4,UUID.randomUUID());p.executeUpdate();}
        var store=new AiOAuthTransactionStore(provider("runtime",tenant,user),true,ISSUER,RESOURCE);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(AiSql.hash(VERIFIER.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        store.begin(tx,web,registration.clientId(),CALLBACK,challenge,"bounded state",AiScope.fromMask(3),nonce);store.approve(tx,web,AiScope.fromMask(3));store.complete(tx,web,AiScope.fromMask(3),code);
        return new AiOAuthCredentialStore(provider("runtime",tenant,user),ISSUER,RESOURCE).redeem(code,registration.clientId(),CALLBACK,VERIFIER,null,null,null,null).accessToken();
    }
    void insertCase(Connection c,int id,int tenant,String name,boolean deleted)throws SQLException {
        sql(c,"SET IDENTITY_INSERT dbo.Cases ON");
        try(var p=c.prepareStatement("INSERT dbo.Cases(Id,ShaleClientId,Name,CaseNumber,IsDeleted,ClientEstate,FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,CreatedAt,UpdatedAt) VALUES(?,?,?,N'NUMBER',?,0,0,0,0,0,0,0,0,0,0,0,0,'2026-10-23T11:22:33.1234567','2026-10-23T11:22:33.1234567')")){p.setInt(1,id);p.setInt(2,tenant);p.setString(3,name);p.setBoolean(4,deleted);p.executeUpdate();}finally{sql(c,"SET IDENTITY_INSERT dbo.Cases OFF");}
    }
    List<String> legacyProcedures()throws Exception {var names=new HashSet<String>();try(var files=Files.list(Path.of("../docs/sql/ai-integration"))){for(Path path:files.filter(p->p.toString().endsWith(".sql")&&!p.getFileName().toString().contains("confinement")).toList()){var m=java.util.regex.Pattern.compile("GRANT EXECUTE ON OBJECT::dbo\\.(\\w+) TO (?:AiIntegrationRuntime|AiIntegrationBootstrap|AiCapabilityOperator)").matcher(Files.readString(path));while(m.find())names.add(m.group(1));}}assertEquals(42,names.size());return names.stream().sorted().toList();}
    DbSessionProvider provider(String role,int tenant,int user){return ()->{try{return connection(role,tenant,user);}catch(SQLException e){throw new IllegalStateException("Local fixture connection failed.",e);}};}
    DataSource source(String role){var s=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();s.setURL(url(db));s.setUser(prefix+"_"+role);s.setPassword(secrets.get(role));return s;}
    Connection connection(String role,Integer tenant,Integer user)throws SQLException {var c=DriverManager.getConnection(url(db),prefix+"_"+role,secrets.get(role));try(var p=c.prepareStatement("EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=?; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=?")){p.setObject(1,tenant);p.setObject(2,user);p.execute();}return c;}
    Connection owner(String database)throws SQLException{return DriverManager.getConnection(url(database).replace("socketTimeout=10000","socketTimeout=30000"),"sa",password);}
    String url(String database){return "jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=true;loginTimeout=3;socketTimeout=10000";}
    static void sql(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.setQueryTimeout(20);boolean result=s.execute(sql);while(true){if(result)try(var r=s.getResultSet()){while(r.next()){} }else if(s.getUpdateCount()==-1)break;result=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);}}}
    static int count(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    static void Utc(PreparedStatement p,int n,Instant instant)throws SQLException{com.shale.data.util.UtcJdbcTimestamp.set(p,n,instant);}
    static byte[] bytes(DisclosureBuffer b){var source=b.bytes();byte[] bytes=new byte[source.remaining()];source.get(bytes);return bytes;}
}
