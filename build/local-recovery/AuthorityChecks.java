import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Focused real SQL regression. Fresh loopback synthetic resources only, retained on success/failure.
 * Reuses the actual sandbox permission recipe and unchanged controlled Apply/Rollback SQL.
 * No Windows, manual recovery, application password/remembered/logout/reboot claim. */
public final class AuthorityChecks {
    static Path root;
    static String db,nonce,app,rt;
    static int port;
    static Map<String,String> vars=new HashMap<>();
    static void require(boolean ok,String why){Sandbox.require(ok,why);}
    static Connection connect(String database,String user,String password)throws Exception {
        var ds=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        ds.setURL("jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=20000");
        ds.setUser(user);ds.setPassword(password);return ds.getConnection();
    }
    static Connection admin(String database)throws Exception{return connect(database,Sandbox.env("SHALE_AUTH_VERIFY_ADMIN_USER"),Sandbox.env("SHALE_AUTH_VERIFY_ADMIN_PASSWORD"));}
    static List<Map<String,String>> run(String script)throws Exception {
        try(var c=admin(db)){return AuthorityCorrection.script(c,root,"docs/sql/auth/permission-cutover/"+script+".sql",vars);}
    }
    static String hash()throws Exception{try(var c=admin(db)){return AuthorityCorrection.baseline(c,root,db);}}
    static void refused(Throwing action,String label)throws Exception {boolean refused=false;try{action.run();}catch(IllegalArgumentException expected){refused=true;}require(refused,label);}
    interface Throwing{void run()throws Exception;}
    static void loginChecks()throws Exception {
        try(var c=admin(db)){
            AuthorityCorrection.originalOwner(c,db,nonce);
            refused(()->Sandbox.checkOwnership(c,db,"0".repeat(32)),"Wrong nonce accepted");
            refused(()->Sandbox.checkOwnership(c,"wrong_database",nonce),"Wrong catalog accepted");
        }
        for(String role:List.of("shale_app","shale_runtime"))try(var c=connect(db,role.equals("shale_app")?app:rt,Sandbox.env("SHALE_AUTH_VERIFY_LOGIN_PASSWORD"))) {
            if(role.equals("shale_runtime"))Sandbox.checkOwnership(c,db,nonce);
            Sandbox.launchIdentity(c,db,role,role.equals("shale_app")?app:rt);
            if(role.equals("shale_app")){
                try{Sandbox.scalar(c,"SELECT COUNT(*) FROM sandbox.Ownership");throw new AssertionError("Auth nonce DATA still readable");}catch(SQLException denied){require(denied.getErrorCode()==229,"Expected actual table denial");}
                require(Sandbox.scalar(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE class=0 AND name=N'ShaleRecoveryNonce'")==0,"Database-level nonce must remain metadata-denied");
            }else{
                Sandbox.sql(c,"EXEC sys.sp_set_session_context N'ShaleClientId',11");
                require(Sandbox.scalar(c,"SELECT COUNT(*) FROM dbo.UserSessions WHERE ShaleClientId=22")==0,"Existing tenant filter leaked");
                require(Sandbox.scalar(c,"SELECT COUNT(*) FROM dbo.UserSessions WHERE ShaleClientId=11")==1,"RLS probe must be nonempty");
            }
        }
    }
    static Map<String,String> dataSnapshot()throws Exception {
        var snapshot=new TreeMap<String,String>();
        try(var c=admin(db)){
            for(var row:AuthorityCorrection.rows(c,"SELECT QUOTENAME(OBJECT_SCHEMA_NAME(object_id))+N'.'+QUOTENAME(name) AS Securable FROM sys.tables WHERE is_ms_shipped=0")) {
                String table=row.get("Securable");
                // dbo is still subject to this fixture's FILTER; snapshot both nonempty tenants.
                for(int tenant:List.of(11,22)) {
                    Sandbox.sql(c,"EXEC sys.sp_set_session_context N'ShaleClientId',"+tenant);
                    try(var q=c.createStatement();var r=q.executeQuery("SELECT * FROM "+table+" FOR XML RAW, BINARY BASE64")) {
                        var xml=new StringBuilder();while(r.next())xml.append(r.getString(1));snapshot.put(table+"/tenant-"+tenant,Sandbox.sha(xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                }
            }
            snapshot.put("ExtendedProperties",AuthorityCorrection.rows(c,"SELECT * FROM sys.extended_properties ORDER BY class,major_id,minor_id,name").toString());
        }return snapshot;
    }
    public static void main(String[] args)throws Exception {
        require("LOCAL_DISPOSABLE_AUTH".equals(System.getenv("SHALE_AUTH_VERIFY_OPT_IN")),"Explicit local opt-in required");
        require(args.length==1,"Explicit repository root required");root=Path.of(args[0]).toRealPath();
        port=Integer.parseInt(Sandbox.env("SHALE_AUTH_VERIFY_PORT"));require(port>=1024&&port<=65535,"Local port required");
        var childEnvironment=new HashMap<String,String>(Map.of("SHALE_SANDBOX_ADMIN_PASSWORD","SYNTHETIC_ADMIN","SHALE_SANDBOX_ADMIN_USER","SYNTHETIC_ADMIN_USER","SHALE_SANDBOX_APP_PASSWORD","SYNTHETIC_APP","JAVA_TOOL_OPTIONS","SYNTHETIC_INJECTION","SAFE_TEST_KEY","retained"));
        Sandbox.scrubChildEnvironment(childEnvironment);
        require(childEnvironment.equals(Map.of("SAFE_TEST_KEY","retained")),"Admin credential/injection escaped into application child environment");
        nonce=UUID.randomUUID().toString().replace("-","");db="ShaleRetirementExercise_"+nonce;app="ShaleRecoveryApp_"+nonce;rt="ShaleRecoveryRuntime_"+nonce;
        System.out.println("SYNTHETIC retained resource="+db);
        try(var c=admin("master")) {
            require(Set.of(2L,3L,4L).contains(Sandbox.scalar(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Local SQL Server only");
            require(Sandbox.scalar(c,"SELECT COUNT(*) FROM sys.databases WHERE name="+Sandbox.literal(db))==0,"Fresh database required");
            Sandbox.sql(c,"CREATE DATABASE "+Sandbox.identifier(db)+" COLLATE SQL_Latin1_General_CP1_CI_AS");
            for(String login:List.of(app,rt))Sandbox.sql(c,"CREATE LOGIN "+Sandbox.identifier(login)+" WITH PASSWORD="+Sandbox.literal(Sandbox.env("SHALE_AUTH_VERIFY_LOGIN_PASSWORD"))+",CHECK_POLICY=ON");
        }
        try(var c=admin(db)) {
            Sandbox.sql(c,"EXEC sys.sp_addextendedproperty @name=N'ShaleRecoveryNonce',@value="+Sandbox.literal(nonce));
            Sandbox.sql(c,"CREATE SCHEMA sandbox AUTHORIZATION dbo");
            Sandbox.sql(c,"CREATE TABLE sandbox.Ownership(Nonce varchar(32) NOT NULL PRIMARY KEY); INSERT sandbox.Ownership VALUES("+Sandbox.literal(nonce)+")");
            Sandbox.sql(c,"CREATE USER shale_app FOR LOGIN "+Sandbox.identifier(app)+"; CREATE USER shale_runtime FOR LOGIN "+Sandbox.identifier(rt)+"; CREATE ROLE shale_runtime_role AUTHORIZATION dbo; ALTER ROLE shale_runtime_role ADD MEMBER shale_runtime");
            // Exact original sandbox ownership grants, including auth metadata DENY.
            Sandbox.sql(c,"GRANT SELECT ON sandbox.Ownership TO shale_app; GRANT SELECT ON sandbox.Ownership TO shale_runtime; DENY VIEW DEFINITION TO shale_app; GRANT EXECUTE TO shale_runtime");
            Sandbox.sql(c,"CREATE TABLE dbo.Users(id int PRIMARY KEY,name_first nvarchar(50),name_last nvarchar(50),email nvarchar(255),email_norm nvarchar(255),password_hash nvarchar(255),password_alg varchar(20),color nvarchar(10),is_attorney bit,is_admin bit,is_deleted bit,default_organization nvarchar(50),organization_id int,initials nvarchar(5),created_at datetime2,ShaleClientId int,LegacyUserId int,Phone nvarchar(50),UpdatedAt datetime2,RowVer rowversion,IsRemoved bit,RemovedAt datetime2,RemovedByUserId int,failed_attempts int,lock_until datetime2,last_login datetime2); CREATE TABLE dbo.AuthConfig(id int,max_attempts int,lock_minutes int)");
            Sandbox.sql(c,"CREATE TABLE dbo.UserSessions(SessionId uniqueidentifier PRIMARY KEY,ShaleClientId int,UserId int,RevokedAt datetime2,ExpiresAt datetime2); CREATE TABLE dbo.DesktopRememberCredentials(ShaleClientId int,UserId int,SessionId uniqueidentifier,InstallationId uniqueidentifier,CredentialHash binary(32),ConsumedAt datetime2,AbsoluteExpiresAt datetime2)");
            for(String table:List.of("ApplicationInstances","UserPreferences","UserReleaseState","UserBoardLanePreferences","EntityActionAuditLog","Statuses"))Sandbox.sql(c,"CREATE TABLE dbo."+table+"(Id int,ShaleClientId int)");
            Sandbox.sql(c,"CREATE SCHEMA sec AUTHORIZATION dbo");
            Sandbox.sql(c,"CREATE FUNCTION sec.fn_FilterByTenant(@tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS Allowed WHERE @tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");
            Sandbox.sql(c,"CREATE SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserSessions WITH(STATE=ON)");
            // Same original auth/old-resolver surface and the Sandbox.provision runtime recipe.
            Sandbox.sql(c,"GRANT SELECT ON dbo.Users TO shale_app AS dbo");
            for(String batch:Sandbox.batches(Files.readString(root.resolve("docs/sql/2026-10-06_desktop_remember_credentials_permissions.sql"))))if(!Sandbox.clean(batch).isBlank())Sandbox.sql(c,batch);
            var tables=AuthorityCorrection.rows(c,"SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo')");
            for(var row:tables){String t=row.get("name");if(!t.equals("Users")&&!t.equals("AuthConfig")&&!t.toLowerCase(Locale.ROOT).contains("audit"))Sandbox.sql(c,"GRANT SELECT ON dbo."+Sandbox.identifier(t)+" TO shale_runtime_role AS dbo");}
            Sandbox.sql(c,"GRANT SELECT (id,name_first,name_last,email,email_norm,color,is_attorney,is_admin,is_deleted,default_organization,organization_id,initials,created_at,ShaleClientId,LegacyUserId,Phone,UpdatedAt,RowVer,IsRemoved,RemovedAt,RemovedByUserId) ON dbo.Users TO shale_runtime_role AS dbo");
            Sandbox.sql(c,"GRANT UPDATE (name_first,name_last,email,initials,color,Phone,UpdatedAt) ON dbo.Users TO shale_runtime_role AS dbo");
            for(String t:List.of("UserSessions","DesktopRememberCredentials","ApplicationInstances","UserPreferences","UserReleaseState","UserBoardLanePreferences"))Sandbox.sql(c,"GRANT INSERT,UPDATE,DELETE ON dbo."+Sandbox.identifier(t)+" TO shale_runtime_role AS dbo");
            Sandbox.sql(c,"GRANT INSERT ON dbo.EntityActionAuditLog TO shale_runtime_role AS dbo; GRANT EXECUTE ON SCHEMA::dbo TO shale_runtime_role AS dbo");
            for(String name:Sandbox.LEGACY){Sandbox.sql(c,Files.readString(root.resolve("shale-server/src/test/resources/auth/"+name+".sql")).replace("\r\n","\n").replace("\n","\r\n"));Sandbox.sql(c,"GRANT EXECUTE ON dbo."+name+" TO shale_app AS dbo");}
            Sandbox.sql(c,"INSERT dbo.Users(id,ShaleClientId,email,password_hash) VALUES(1,11,N'synthetic@example.invalid',N'unchanged-synthetic-hash'); INSERT dbo.UserSessions VALUES(NEWID(),11,1,NULL,DATEADD(HOUR,1,SYSUTCDATETIME())),(NEWID(),22,3,NULL,DATEADD(HOUR,1,SYSUTCDATETIME()))");
        }
        vars.put("ExpectedDatabase",db);vars.put("ReviewedIdentityMapping","CONFIRMED");vars.put("LegacyRetirementApproval","APPROVED");vars.put("RuntimeLegacyDisposition","DENY_THREE_MODULES");vars.put("ExclusiveChangeWindow","CONFIRMED");
        vars.put("LegacyAcceptanceRoute","CONTROLLED_CHANGE");vars.put("CompletedPackageReview","APPROVED");vars.put("ControlledWindowAuthorization","AUTHORIZED");vars.put("UnknownClientRisk","ACCEPTED_NOT_ZERO_PROOF");
        for(String key:List.of("IrrecoverableEffectsRisk","ReopenedCapabilityRisk"))vars.put(key,"ACCEPTED");
        for(String key:List.of("RepresentativeChecks","RollbackRehearsal"))vars.put(key,"PASSED");
        for(String key:List.of("SecurityOwner","ServiceOwner","RiskApprovalEvidence","PackageReviewEvidence","WindowAuthorizationEvidence","KnownArtifactEvidence","CompatibilityEvidence","OperationalReadinessEvidence","RollbackRehearsalEvidence"))vars.put(key,"SYNTHETIC_TEST_ONLY");
        var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);vars.put("WindowStartUtc",now.minusMinutes(1).toString());vars.put("WindowEndUtc",now.plusMinutes(28).toString());vars.put("RollbackStartByUtc",now.plusMinutes(17).toString());
        var evidence=new LinkedHashMap<String,Object>();
        String original=hash();vars.put("LegacyBeforeHash",original);
        var originalData=dataSnapshot();
        try(var c=admin(db)){
            AuthorityCorrection.originalOwner(c,db,nonce);var output=new ArrayList<Map<String,String>>();var checks=AuthorityCorrection.diagnostics(c,root,output);evidence.put("OriginalDiagnosticOutput",output);evidence.put("OriginalAuthToken",AuthorityCorrection.token(c,"shale_app"));evidence.put("OriginalRuntimeToken",AuthorityCorrection.token(c,"shale_runtime"));evidence.put("OriginalCompleteAuthChecks",checks);evidence.put("OriginalCompleteRuntimeChecks",AuthorityCorrection.runtimeChecks(c));AuthorityCorrection.expectedFindings(checks,false);AuthorityCorrection.runtimeCheck(c);
            var unknown=new ArrayList<Map<String,String>>();for(var check:checks)unknown.add(new LinkedHashMap<>(check));
            unknown.get(0).put("EffectiveValue",null);
            refused(()->AuthorityCorrection.expectedFindings(unknown,false),"UNKNOWN evidence accepted");
            require(AuthorityCorrection.JSON.toJson(unknown).contains("\"EffectiveValue\": null"),"NULL diagnostic evidence was omitted");
            Sandbox.sql(c,"GRANT UPDATE ON dbo.Statuses TO shale_app AS dbo");AuthorityCorrection.clear(c);
            var extra=AuthorityCorrection.diagnostics(c,root);refused(()->AuthorityCorrection.expectedFindings(extra,false),"Additional conflict accepted");
            Sandbox.sql(c,"REVOKE UPDATE ON dbo.Statuses FROM shale_app AS dbo");
            Sandbox.sql(c,"GRANT ALTER ON dbo.Statuses TO shale_runtime AS dbo");
            var runtimeExtra=AuthorityCorrection.runtimeChecks(c);
            refused(()->AuthorityCorrection.requireRuntimeChecks(runtimeExtra),"Additional runtime escalation accepted");
            Sandbox.sql(c,"REVOKE ALTER ON dbo.Statuses FROM shale_runtime AS dbo");
        }
        // Reproduce the actual post-mutation failure with unchanged SQL and exactly two findings.
        var failedApply=new ArrayList<Map<String,String>>();
        try(var c=admin(db)) {
            try{AuthorityCorrection.script(c,root,"docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql",vars,failedApply);throw new AssertionError("Original sandbox unexpectedly passed guarded Apply");}catch(SQLException e){require(e.getErrorCode()==58307,"Must reproduce SQL 58307");}
        }
        var failures=failedApply.stream().filter(r->r.containsKey("ConditionName")&&r.containsKey("Securable")).toList();
        require(failures.size()==2&&failures.stream().allMatch(r->r.get("Securable").startsWith("[sandbox].[Ownership]")&&"SELECT".equals(r.get("PermissionName"))&&"1".equals(r.get("EffectiveValue"))),"The COMPLETE failed Apply findings must be exactly ownership object/column SELECT");
        evidence.put("FailedGuardOutputs",failedApply);
        require(original.equals(hash()),"Failed Apply must restore full original catalog");
        try(var c=admin(db)){
            var result=AuthorityCorrection.script(c,root,"build/local-recovery/correct-authority.sql",Map.of("ExpectedDatabase",db,"OwnershipNonce",nonce,"LegacyBeforeHash",original));
            require(result.stream().anyMatch(r->"LocalOwnershipCorrected".equals(r.get("CheckName"))),"Correction commit marker required");
        }
        require(originalData.equals(dataSnapshot()),"Correction changed application/ownership rows or extended properties");
        String corrected=hash();require(!corrected.equals(original),"Reviewed ACL transition must change baseline");vars.put("LegacyBeforeHash",corrected);loginChecks();
        try(var c=admin(db)){var checks=AuthorityCorrection.diagnostics(c,root);evidence.put("CorrectedCompleteAuthChecks",checks);evidence.put("CorrectedCompleteRuntimeChecks",AuthorityCorrection.runtimeChecks(c));AuthorityCorrection.expectedFindings(checks,true);AuthorityCorrection.runtimeCheck(c);}
        var applied=run("controlled-retire-legacy-login");
        String after=applied.stream().filter(r->"LegacyRetirementCommitted".equals(r.get("CheckName"))).findFirst().orElseThrow().get("AfterHash");vars.put("LegacyAfterHash",after);
        loginChecks();
        vars.put("LegacyRollbackApproval","APPROVED");vars.put("RollbackCompatibilityReviewed","CONFIRMED");run("controlled-rollback-legacy-login");
        require(corrected.equals(hash()),"Rollback must restore EXACT full corrected catalog");loginChecks();
        require(originalData.equals(dataSnapshot()),"Apply/rollback/launch checks changed existing rows or extended properties");
        try(var c=admin(db)){
            require(Sandbox.scalar(c,"SELECT COUNT(*) FROM dbo.Users WHERE id=1 AND password_hash=N'unchanged-synthetic-hash'")==1,"Application rows changed");
            Sandbox.sql(c,"BEGIN TRANSACTION; UPDATE sandbox.Ownership SET Nonce='00000000000000000000000000000000'");
            refused(()->Sandbox.checkOwnership(c,db,nonce),"Changed nonce silently accepted");
            Sandbox.sql(c,"ROLLBACK");
        }
        evidence.put("OriginalBaseline",original);evidence.put("CorrectedBaseline",corrected);evidence.put("RetiredBaseline",after);evidence.put("Resource",db);
        Path log=root.resolve("work/authority/synthetic-"+nonce+".json");Files.createDirectories(log.getParent());Files.writeString(log,AuthorityCorrection.JSON.toJson(evidence),StandardOpenOption.CREATE_NEW);
        System.out.println("Complete diagnostics retained: "+log);
        System.out.println("PASS full guarded 58307 reproduction/original equality, complete diagnostics/extra-conflict refusal, exact correction delta, real-login launch/ownership/tenant checks before-during-after, guarded Apply and full corrected-baseline rollback equality; resources retained; manual recovery NOT MEASURED");
        System.out.println("OriginalSyntheticBaseline="+original+" CorrectedSyntheticBaseline="+corrected+" RetiredSyntheticBaseline="+after);
    }
}
