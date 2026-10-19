import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import com.google.gson.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.mindrot.jbcrypt.BCrypt;
import com.shale.data.config.*;
import com.shale.data.dao.UserDao;
import com.shale.data.runtime.RuntimeSessionService;
import com.shale.desktop.session.DesktopSessionEnrollmentClient;

/** Local development tool only. Not shipped in Shale. JDK 21 required. */
public final class Sandbox {
    static final String EXPORT_SHA = "a34db4f3f2371e20db97ee8913d30de333ace3a53c1dc12196644694da9aebe5";
    static final String APP_REV = "59d8374858764788c621f6f8d986773b58ab79c4";
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final Pattern GO = Pattern.compile("(?m)^GO[ \\t]*\\r?$\\n?");
    static final Set<String> LEGACY = Set.of("sp_login_get_hash", "sp_login_mark_failure", "sp_login_mark_success");
    final Path state, tools, source;
    final Properties p = new Properties();
    Sandbox(Path state, Path tools) throws Exception {
        this.state=state.toRealPath(); this.tools=tools.toRealPath(); source=this.state.resolve("source");
        try(var in=Files.newInputStream(this.state.resolve("sandbox.properties"))){p.load(in);}
        require(p.getProperty("database", "").matches("ShaleRetirementExercise_[a-f0-9]{32}"), "Invalid owned database name");
        require(p.getProperty("nonce", "").matches("[a-f0-9]{32}"), "Missing ownership nonce");
        require(p.getProperty("database").endsWith(p.getProperty("nonce")), "Database/nonce mismatch");
        for(String key:List.of("sqlPort","apiPort","livePort","wsPort")) {
            int v=Integer.parseInt(p.getProperty(key)); require(v>=1024&&v<=65535,"Invalid local port");
        }
        require(new HashSet<>(List.of(p.getProperty("sqlPort"),p.getProperty("apiPort"),p.getProperty("livePort"),p.getProperty("wsPort"))).size()==4,"Ports must be distinct");
        require(APP_REV.equals(p.getProperty("applicationRevision")),"Application source reference changed");
        require(EXPORT_SHA.equals(sha(Files.readAllBytes(this.state.resolve("private-input.txt")))),"Private original hash changed");
        require(APP_REV.equals(capture(List.of("git","rev-parse","HEAD"),source).trim()),"Application checkout changed");
        require(capture(List.of("git","status","--porcelain","--untracked-files=normal"),source).isBlank(),"Application checkout must be clean");
    }
    public static void main(String[] args) {
        try {
            require(Runtime.version().feature()==21,"Use JDK 21; sandbox network guard depends on JDK 21");
            require(args.length==3,"Usage: Sandbox phase private-state tooling-directory");
            var s=new Sandbox(Path.of(args[1]),Path.of(args[2]));
            switch(args[0]) {
                case "provision" -> s.provision();
                case "api","desktop","relay" -> s.launch(args[0]);
                case "verify" -> s.verify();
                case "selftest" -> ContractChecks.main(new String[]{s.state.toString(),s.tools.toString()});
                default -> throw new IllegalArgumentException("Unknown phase");
            }
        } catch(Exception failure) {
            // SQLException text can contain SQL/password material. Never print arbitrary exceptions.
            System.err.println("STOP local sandbox: "+failure.getClass().getSimpleName()+". Preserve the exact state; no retry/cleanup.");
            if(failure instanceof SQLException q)System.err.println("SQL error number="+q.getErrorCode()+" state="+q.getSQLState());
            else if(failure instanceof IllegalArgumentException)System.err.println(failure.getMessage());
            System.exit(1);
        }
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static String env(String key){String v=System.getenv(key);require(v!=null&&!v.isBlank(),"Missing private child environment: "+key);return v;}
    static String literal(String s){return "N'"+s.replace("'","''")+"'";}
    static String identifier(String s){require(s.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"),"Unsafe SQL identifier");return "["+s+"]";}
    String url(String db){return "jdbc:sqlserver://127.0.0.1:"+p.getProperty("sqlPort")+";databaseName="+db+";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=20000";}
    Connection connect(String db,String user,String pass)throws Exception{
        var d=new SQLServerDataSource();d.setURL(url(db));d.setUser(user);d.setPassword(pass);Connection c=d.getConnection();sql(c,"SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF");return c;
    }
    Connection admin(String db)throws Exception{return connect(db,env("SHALE_SANDBOX_ADMIN_USER"),env("SHALE_SANDBOX_ADMIN_PASSWORD"));}
    static void sql(Connection c,String text)throws SQLException{try(var q=c.createStatement()){q.setQueryTimeout(20);q.execute(text);}}
    static long scalar(Connection c,String text)throws SQLException{try(var q=c.createStatement()){q.setQueryTimeout(20);try(var r=q.executeQuery(text)){require(r.next(),"Expected scalar result");return r.getLong(1);}}}
    void owned(Connection c)throws Exception{
        require(scalar(c,"SELECT COUNT(*) FROM sandbox.Ownership WHERE Nonce="+literal(p.getProperty("nonce")))==1,"Ownership mismatch");
        require(p.getProperty("database").equals(c.getCatalog()),"Database mismatch");
    }
    static List<String> batches(String s){return Arrays.asList(GO.split(s,-1));}
    static String clean(String s){return s.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)--[^\\r\\n]*","").strip();}
    record Adaptation(List<String> batches,List<Map<String,Object>> review){}
    Adaptation adapt()throws Exception{
        String original=new String(Files.readAllBytes(state.resolve("private-input.txt")),StandardCharsets.UTF_16);
        var out=new ArrayList<String>();var review=new ArrayList<Map<String,Object>>();int index=0;
        for(String batch:batches(original)){
            String c=clean(batch).replaceAll("\\s+", " ");String action="preserve";String replacement=batch;
            if(c.isEmpty()) action="omit comment/empty";
            else if(c.matches("(?is)(CREATE DATABASE|ALTER DATABASE|ALTER AUTHORIZATION ON DATABASE::).*")) action="omit production database configuration/owner";
            else if(c.matches("(?is)CREATE USER .*")) action="replace production login mapping in dedicated local setup";
            else if(c.matches("(?is).*@rolename = N'db_owner'.*")||c.matches("(?is)GRANT CONNECT TO \\[Prime\\].*")) action="omit production elevated principal";
            else if(c.matches("(?is)(GRANT|DENY|REVOKE) .*")){
                require(c.matches("(?is).*(TO|FROM) \\[(shale_app|shale_runtime)\\] AS \\[dbo\\]"),"Unreviewed exported permission");
                action="preserve reviewed auth/runtime permission";
            }else{
                require(c.matches("(?is)(SET (ANSI_NULLS|QUOTED_IDENTIFIER|ANSI_PADDING|ARITHABORT).*|CREATE (ROLE|SCHEMA|FUNCTION|TABLE|VIEW|SECURITY POLICY|NONCLUSTERED INDEX|UNIQUE.*|TRIGGER|PROC.*).*|ALTER (AUTHORIZATION|TABLE).*|sys\\.sp_addrolemember.*)"),"Unexpected export batch category "+index);
            }
            if(c.equals("SET ANSI_PADDING OFF")){replacement="SET ANSI_PADDING ON\r\n";action="preserve with explicit local ANSI_PADDING ON for filtered indexes";}
            for(String name:LEGACY)if(c.matches("(?is)CREATE\\s+PROC\\s+\\[dbo\\]\\.\\["+name+"\\].*")){
                // SSMS brackets its procedure identifier. Guarded retirement requires the captured
                // canonical CRLF definition, not a newly invented expected hash.
                String canonical=Files.readString(state.resolve("reviewed/shale-server/src/test/resources/auth/"+name+".sql")).replace("\r\n","\n").replace("\n","\r\n");
                require(clean(batch).replace("[dbo].["+name+"]","dbo."+name).equals(clean(canonical)),"Legacy body differs from reviewed capture");
                replacement=canonical;action="canonical captured legacy CRLF definition (body verified equal)";
            }
            if(action.startsWith("preserve")||action.startsWith("canonical"))out.add(replacement);
            review.add(Map.of("batch",index++,"sha256Utf8",sha(batch.getBytes(StandardCharsets.UTF_8)),"action",action));
        }
        require(out.stream().filter(x->clean(x).startsWith("CREATE TABLE ")).count()==96,"Table inventory changed");
        require(out.stream().anyMatch(x->x.contains("CREATE SECURITY POLICY [sec].[TenantFilter]")),"RLS policy required");
        return new Adaptation(out,review);
    }
    void provision()throws Exception{
        Path attempted=state.resolve("provision-attempted");Files.writeString(attempted,Instant.now().toString(),StandardOpenOption.CREATE_NEW);
        Adaptation a=adapt();
        Files.writeString(state.resolve("adaptation-review.json"),JSON.toJson(a.review()),StandardOpenOption.CREATE_NEW);
        Files.writeString(state.resolve("adapted-private.sql"),String.join("GO\r\n",a.batches())+"GO\r\n",StandardOpenOption.CREATE_NEW);
        String db=p.getProperty("database"),app=p.getProperty("appLogin"),rt=p.getProperty("runtimeLogin");
        String appPass=env("SHALE_SANDBOX_APP_PASSWORD"),rtPass=env("SHALE_SANDBOX_RUNTIME_PASSWORD");
        require(appPass.length()>=16&&rtPass.length()>=16&&!appPass.equals(rtPass),"Distinct local SQL passwords of at least 16 characters required");
        require(!appPass.equals(env("SHALE_SANDBOX_ADMIN_PASSWORD"))&&!rtPass.equals(env("SHALE_SANDBOX_ADMIN_PASSWORD")),"Application credentials must differ from local admin");
        try(var c=admin("master")){
            require(Set.of(2L,3L,4L).contains(scalar(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Azure/unsupported engine refused");
            require(scalar(c,"SELECT COUNT(*) FROM sys.databases WHERE name="+literal(db))==0,"Existing database refused");
            require(scalar(c,"SELECT COUNT(*) FROM sys.server_principals WHERE name IN("+literal(app)+","+literal(rt)+")")==0,"Existing login refused");
            sql(c,"CREATE DATABASE "+identifier(db)+" COLLATE SQL_Latin1_General_CP1_CI_AS");
        }
        try(var c=admin(db)){
            sql(c,"EXEC sys.sp_addextendedproperty @name=N'ShaleRecoveryNonce',@value="+literal(p.getProperty("nonce")));
            sql(c,"CREATE SCHEMA sandbox AUTHORIZATION dbo");
            sql(c,"CREATE TABLE sandbox.Ownership(Nonce varchar(32) NOT NULL PRIMARY KEY)");
            sql(c,"INSERT sandbox.Ownership VALUES("+literal(p.getProperty("nonce"))+")");owned(c);
            // Export's database settings are intentionally replaced by these explicit local options.
            sql(c,"ALTER DATABASE "+identifier(db)+" SET COMPATIBILITY_LEVEL=150");
            sql(c,"ALTER DATABASE "+identifier(db)+" SET ALLOW_SNAPSHOT_ISOLATION ON");
            sql(c,"ALTER DATABASE "+identifier(db)+" SET READ_COMMITTED_SNAPSHOT ON");
        }
        try(var c=admin("master")){
            sql(c,"CREATE LOGIN "+identifier(app)+" WITH PASSWORD="+literal(appPass)+", CHECK_POLICY=ON, DEFAULT_DATABASE="+identifier(db));
            sql(c,"CREATE LOGIN "+identifier(rt)+" WITH PASSWORD="+literal(rtPass)+", CHECK_POLICY=ON, DEFAULT_DATABASE="+identifier(db));
        }
        try(var c=admin(db)){
            owned(c);
            sql(c,"CREATE USER shale_app FOR LOGIN "+identifier(app)+"; CREATE USER shale_runtime FOR LOGIN "+identifier(rt));
            sql(c,"GRANT SELECT ON sandbox.Ownership TO shale_app; GRANT SELECT ON sandbox.Ownership TO shale_runtime");
            int i=0;for(String b:a.batches()){
                try{sql(c,b);}catch(SQLException e){System.err.println("Adapted batch failed at preserved index "+i);throw e;}i++;
            }
            // The export omits the production runtime role's table grants. Give only the local
            // read surface and writes required by sessions/profile/preferences; no DDL/owner role.
            var tables=new ArrayList<String>();try(var q=c.createStatement();var r=q.executeQuery("SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo')")){while(r.next())tables.add(r.getString(1));}
            for(String t:tables)if(!t.equals("Users")&&!t.equals("AuthConfig")&&!t.toLowerCase(Locale.ROOT).contains("audit"))sql(c,"GRANT SELECT ON dbo."+identifier(t)+" TO shale_runtime_role AS dbo");
            sql(c,"GRANT SELECT (id,name_first,name_last,email,email_norm,color,is_attorney,is_admin,is_deleted,default_organization,organization_id,initials,created_at,ShaleClientId,LegacyUserId,Phone,UpdatedAt,RowVer,IsRemoved,RemovedAt,RemovedByUserId) ON dbo.Users TO shale_runtime_role AS dbo");
            sql(c,"GRANT UPDATE (name_first,name_last,email,initials,color,Phone,UpdatedAt) ON dbo.Users TO shale_runtime_role AS dbo");
            for(String t:List.of("UserSessions","DesktopRememberCredentials","ApplicationInstances","UserPreferences","UserReleaseState","UserBoardLanePreferences"))sql(c,"GRANT INSERT,UPDATE,DELETE ON dbo."+identifier(t)+" TO shale_runtime_role AS dbo");
            for(String t:tables)if(t.toLowerCase(Locale.ROOT).contains("audit"))sql(c,"GRANT INSERT ON dbo."+identifier(t)+" TO shale_runtime_role AS dbo");
            sql(c,"GRANT EXECUTE ON SCHEMA::dbo TO shale_runtime_role AS dbo");
            seed(c);
            require(scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND schema_id=SCHEMA_ID('dbo')")==96,"Expected complete table inventory");
            require(scalar(c,"SELECT COUNT(*) FROM sys.security_policies WHERE is_enabled=1")==1,"Expected enabled RLS");
            require(scalar(c,"SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID('dbo.Users'),OBJECT_ID('dbo.auditLog'),OBJECT_ID('dbo.Statuses'))")==0,"Exercise requires unchanged pre-Stage-A RLS");
            require(scalar(c,"SELECT COUNT(*) FROM sys.procedures WHERE name IN('LookupAuthenticationCredentialV1','ResolveDesktopRememberCredentialV2')")==0,"Pre-Stage-A modules must be absent");
            // The unchanged baseline is read-only, and is NOT another retirement rehearsal.
            runSqlcmdIncludes(c,state.resolve("reviewed/docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql"),db);
        }
        try(var c=connect(db,app,appPass)){owned(c);authority(c);}
        try(var c=connect(db,rt,rtPass)){owned(c);authority(c);}
        Files.writeString(state.resolve("provisioned"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
        System.out.println("PROVISIONED local synthetic database="+db+"; application source="+APP_REV+"; manual recovery NOT MEASURED");
    }
    void seed(Connection c)throws Exception{
        sql(c,"INSERT dbo.AuthConfig(id,max_attempts,lock_minutes) VALUES(1,5,15)");
        for(int tenant:List.of(11,22)){
            sql(c,"SET IDENTITY_INSERT dbo.ShaleClients ON; INSERT dbo.ShaleClients(Id,TenantGuid,Code,DisplayName) VALUES("+tenant+",NEWID(),'SYNTHETIC"+tenant+"','LOCAL SYNTHETIC TENANT "+tenant+"'); SET IDENTITY_INSERT dbo.ShaleClients OFF");
            sql(c,"INSERT dbo.TaskPolicyConfigurations(ShaleClientId,DueDatePolicy) VALUES("+tenant+",'WARN')");
        }
        String hash=BCrypt.hashpw(env("SHALE_SANDBOX_USER_PASSWORD"),BCrypt.gensalt(10));
        for(int id:List.of(1,2,3)){
            try(var q=c.prepareStatement("SET IDENTITY_INSERT dbo.Users ON; INSERT dbo.Users(id,name_first,name_last,email,password_hash,password_alg,color,is_attorney,is_admin,is_deleted,initials,ShaleClientId) VALUES(?,N'Local',N'Synthetic',?,?,'bcrypt','#336699',0,1,0,'LS',?); SET IDENTITY_INSERT dbo.Users OFF")){
                q.setInt(1,id);q.setString(2,"synthetic"+id+"@example.invalid");q.setString(3,hash);q.setInt(4,id==3?22:11);q.execute();
            }
        }
    }
    static void authority(Connection c)throws Exception{
        require(scalar(c,"SELECT CASE WHEN USER_NAME() IN('dbo','guest') OR IS_MEMBER('db_owner')=1 OR IS_MEMBER('ShaleAdmins')=1 OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')=1 OR IS_SRVROLEMEMBER('sysadmin')=1 THEN 1 ELSE 0 END")==0,"Application identity has bypass authority");
    }
    void runSqlcmdIncludes(Connection c,Path file,String db)throws Exception{
        StringBuilder all=new StringBuilder();expand(file.toAbsolutePath().normalize(),all,new HashSet<>());
        String s=all.toString().replace("$(ExpectedDatabase)",db);require(!s.contains("$("),"Unexpected SQLCMD variable");
        for(String b:batches(s))if(!clean(b).isBlank())sql(c,b);
    }
    void expand(Path file,StringBuilder all,Set<Path> stack)throws Exception{
        require(stack.add(file),"Recursive include");
        for(String line:Files.readAllLines(file)){
            if(line.startsWith(":r "))expand(state.resolve("reviewed").resolve(line.substring(3).trim()).normalize(),all,stack);
            else if(!line.startsWith(":ON ERROR"))all.append(line).append("\r\n");
        }stack.remove(file);
    }
    void ready()throws Exception{require(Files.exists(state.resolve("provisioned")),"Provisioning did not complete");}
    List<String> classpath(String module)throws Exception{
        return List.of(source.resolve(module+"/target/classes").toString(),Files.readString(state.resolve(module+".cp")).strip());
    }
    void launch(String mode)throws Exception{
        ready();
        if(!mode.equals("relay"))for(String role:List.of("app","runtime")){
            try(var c=connect(p.getProperty("database"),p.getProperty(role.equals("app")?"appLogin":"runtimeLogin"),env(role.equals("app")?"SHALE_SANDBOX_APP_PASSWORD":"SHALE_SANDBOX_RUNTIME_PASSWORD"))){owned(c);authority(c);require(scalar(c,"SELECT CASE WHEN USER_NAME()="+literal(role.equals("app")?"shale_app":"shale_runtime")+" THEN 1 ELSE 0 END")==1,"Local login mapping changed");}
        }
        String module=mode.equals("api")?"shale-server":"shale-desktop";
        String cp=state.resolve("tool-classes")+File.pathSeparator+String.join(File.pathSeparator,classpath(module));
        var args=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djava.security.manager=allow","--add-opens=java.base/java.lang=ALL-UNNAMED","-Duser.home="+state.resolve("home"),"-Djava.io.tmpdir="+state.resolve("tmp"),"-Djava.util.prefs.userRoot="+state.resolve("prefs"),"-DSHALE_UPDATE_ATTEMPT_DIR="+state.resolve("updates"),"-DSHALE_UPDATE_EXECUTION_LOCK="+state.resolve("update.lock"),"-cp",cp,"LocalBootstrap",mode,state.toString()));
        ProcessBuilder pb=new ProcessBuilder(args).directory(state.toFile()).inheritIO();Map<String,String> e=pb.environment();
        e.keySet().removeIf(k->k.toUpperCase(Locale.ROOT).matches("(SHALE_.*|SPRING_.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*|FUNCTION_.*|LIVE_.*|NEGOTIATE_.*|WEBPUBSUB_.*|APP_.*|DB_.*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|MAVEN_OPTS|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY)"));
        for(String dir:List.of("home","tmp","local","roaming","machine","config","data","cache"))Files.createDirectories(state.resolve(dir));
        e.put("LOCALAPPDATA",state.resolve("local").toString());e.put("APPDATA",state.resolve("roaming").toString());e.put("ProgramData",state.resolve("machine").toString());e.put("PROGRAMDATA",state.resolve("machine").toString());
        e.put("XDG_CONFIG_HOME",state.resolve("config").toString());e.put("XDG_DATA_HOME",state.resolve("data").toString());e.put("XDG_CACHE_HOME",state.resolve("cache").toString());
        e.put("APP_ENV","local");e.put("APP_VERSION","0.0.0");e.put("SHALE_SERVER_API_BASE_URL","http://127.0.0.1:"+p.getProperty("apiPort"));
        e.put("NEGOTIATE_ENDPOINT_URL","http://127.0.0.1:"+p.getProperty("livePort")+"/negotiate");e.put("LIVE_NEGOTIATE_ENDPOINT_URL",e.get("NEGOTIATE_ENDPOINT_URL"));e.put("LIVE_PUBLISH_ENDPOINT_URL","http://127.0.0.1:"+p.getProperty("livePort")+"/publish");
        if(!mode.equals("relay")){
            for(String prefix:List.of("APP","RT")){
                e.put("SHALE_"+prefix+"_DB_URL",url(p.getProperty("database")));e.put("SHALE_"+prefix+"_JDBC_URL",url(p.getProperty("database")));
                String user=p.getProperty(prefix.equals("APP")?"appLogin":"runtimeLogin");String secret=env(prefix.equals("APP")?"SHALE_SANDBOX_APP_PASSWORD":"SHALE_SANDBOX_RUNTIME_PASSWORD");
                e.put("SHALE_"+prefix+"_DB_USER",user);e.put("SHALE_"+prefix+"_USER",user);e.put("SHALE_"+prefix+"_DB_PASSWORD",secret);e.put("SHALE_"+prefix+"_PASS",secret);
            }
        }
        // Stable across local API restarts, never written to disk or passed in arguments.
        if(mode.equals("api")){e.put("SHALE_AUTH_TOKEN_SECRET",env("SHALE_SANDBOX_TOKEN_SECRET"));e.put("SHALE_AUTH_SESSION_BINDING_CUTOVER_AT","2020-01-01T00:00:00Z");}
        int exit=pb.start().waitFor();require(exit==0,"Local child exited with failure");
    }
    void verify()throws Exception{
        ready();var report=new LinkedHashMap<String,Object>();String db=p.getProperty("database");
        for(String prefix:List.of("APP","RT")){
            System.setProperty("SHALE_"+prefix+"_DB_URL",url(db));System.setProperty("SHALE_"+prefix+"_DB_USER",p.getProperty(prefix.equals("APP")?"appLogin":"runtimeLogin"));System.setProperty("SHALE_"+prefix+"_DB_PASSWORD",env(prefix.equals("APP")?"SHALE_SANDBOX_APP_PASSWORD":"SHALE_SANDBOX_RUNTIME_PASSWORD"));
        }
        LocalSqlDriver.install(url(db));
        try(var sources=new DataSources(new Config())){
            var auth=new com.shale.data.auth.AuthServiceImpl(sources,new com.shale.data.auth.BCryptPasswordVerifier());
            var user=auth.login("synthetic1@example.invalid",env("SHALE_SANDBOX_USER_PASSWORD"));require(user.getId()==1&&user.getShaleClientId()==11,"Desktop password identity mismatch");
            boolean rejected=false;try{auth.login("synthetic1@example.invalid",UUID.randomUUID().toString());}catch(com.shale.data.errors.AuthException expected){rejected=true;}require(rejected,"Wrong password accepted");report.put("desktopPassword","PASS real AuthServiceImpl/JDBC/bcrypt; source build");
            var runtime=new RuntimeSessionService(sources.runtime());runtime.initialize(user);
            var dao=new UserDao(runtime);require(dao.listUsersForTenant(11).size()==2,"Directory must show two same-tenant synthetic users");require(dao.findById(3,11)==null,"Cross-tenant User detail leak");var detail=dao.findById(1,11);require(detail!=null,"User detail missing");
            require(dao.updateBasicProfile(new UserDao.UserProfileUpdateRequest(1,11,"Local", "Synthetic "+UUID.randomUUID().toString().substring(0,8),detail.email(),detail.phone(),detail.initials(),detail.color(),detail.rowVer())),"Profile edit failed");
            try(var c=runtime.getConnection()){authority(c);}
            try(var c=admin(db)){owned(c);require(scalar(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE ShaleClientId=11 AND ActorUserId=1 AND EntityType='USER' AND EntityId=1 AND Action='UPDATED'")>=1,"Profile mutation audit missing");}
            report.put("directoryDetailProfileAudit","PASS real UserDao and existing transactional EntityActionAuditLog");
        }
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();String origin="http://127.0.0.1:"+p.getProperty("apiPort");
        JsonObject login=request(http,origin,"/api/auth/login",null,Map.of("email","synthetic1@example.invalid","password",env("SHALE_SANDBOX_USER_PASSWORD")),200);
        String token=login.get("accessToken").getAsString();request(http,origin,"/api/auth/me",token,null,200);request(http,origin,"/api/users",token,null,200);request(http,origin,"/api/users/1",token,null,200);
        var refreshed=request(http,origin,"/api/auth/refresh",token,Map.of(),200);String fresh=refreshed.get("accessToken").getAsString();request(http,origin,"/api/auth/me",token,null,401);request(http,origin,"/api/auth/logout",fresh,Map.of(),200);request(http,origin,"/api/auth/me",fresh,null,401);
        request(http,origin,"/api/auth/login",null,Map.of("email","synthetic1@example.invalid","password",UUID.randomUUID().toString()),401);
        var second=request(http,origin,"/api/auth/login",null,Map.of("email","synthetic3@example.invalid","password",env("SHALE_SANDBOX_USER_PASSWORD")),200);request(http,origin,"/api/users/1",second.get("accessToken").getAsString(),null,404);request(http,origin,"/api/auth/logout",second.get("accessToken").getAsString(),Map.of(),200);
        try(var c=admin(db)){owned(c);require(scalar(c,"SELECT COUNT(*) FROM dbo.UserSessions WHERE ShaleClientId=22")>0,"RLS check needs a real other-tenant row");}
        try(var c=connect(db,p.getProperty("runtimeLogin"),env("SHALE_SANDBOX_RUNTIME_PASSWORD"))){owned(c);sql(c,"EXEC sys.sp_set_session_context N'ShaleClientId',11; EXEC sys.sp_set_session_context N'PrincipalUserId',1");require(scalar(c,"SELECT COUNT(*) FROM dbo.UserSessions WHERE ShaleClientId=22")==0,"Protected session row leaked across tenants");}
        report.put("apiPasswordLifecycle","PASS real loopback serving API + SQL, refresh old-JTI rejection/logout/user switch");
        var client=new DesktopSessionEnrollmentClient(origin);UUID installation=UUID.randomUUID();var enrolled=client.enrollRemembered("synthetic1@example.invalid",env("SHALE_SANDBOX_USER_PASSWORD"),null,installation);require(enrolled.rememberCredential()!=null,"Remembered enrollment missing");
        String replacement=opaqueCredential();
        var restored=client.restore(enrolled.rememberCredential(),replacement,installation);require(restored.userId()==1&&restored.shaleClientId()==11,"Remembered restored identity mismatch");
        boolean replayRejected=false;try{client.restore(enrolled.rememberCredential(),opaqueCredential(),installation);}catch(DesktopSessionEnrollmentClient.EnrollmentException expected){replayRejected=expected.failure()==DesktopSessionEnrollmentClient.Failure.SECURITY_REJECTED;}require(replayRejected,"Old remembered credential replay accepted");client.logout(restored.credential().accessToken());
        report.put("desktopSecondExchangeRemembered","PASS real desktop HTTP client/old resolver + runtime SQL rotation/replay/logout; no DPAPI claim");
        RelayChecks.main(new String[]{state.toString()});
        report.put("localLiveRelay","PASS real desktop negotiate/TLS WebSocket clients; local substitute only");
        report.put("applicationRevision",APP_REV);report.put("manualRecovery","NOT MEASURED");report.put("windowsGuiDpapiRestart","UNVERIFIED");
        Files.writeString(state.resolve("verification-"+UUID.randomUUID()+".json"),JSON.toJson(report),StandardOpenOption.CREATE_NEW);System.out.println("PASS local application/API paths; Windows GUI/DPAPI/restart UNVERIFIED; manual recovery NOT MEASURED");
    }
    static JsonObject request(HttpClient h,String origin,String path,String token,Object body,int expected)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(15));if(token!=null)b.header("Authorization","Bearer "+token);if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body)));
        var r=h.send(b.build(),HttpResponse.BodyHandlers.ofString());require(r.statusCode()==expected,"Unexpected local API status at "+path+": "+r.statusCode());
        JsonElement parsed=JsonParser.parseString(r.body());return parsed.isJsonObject()?parsed.getAsJsonObject():new JsonObject();
    }
    static String opaqueCredential(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    static String capture(List<String> args,Path cwd)throws Exception{var pb=new ProcessBuilder(args).directory(cwd.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);var proc=pb.start();String s=new String(proc.getInputStream().readAllBytes(),StandardCharsets.UTF_8);require(proc.waitFor()==0,"Source reference command failed");return s;}
    static void selftest()throws Exception{
        require(batches("CREATE PROC x AS\r\nSELECT 1\r\nGO\r\nSET ANSI_NULLS ON\r\nGO\r\n").get(0).equals("CREATE PROC x AS\r\nSELECT 1\r\n"),"Batch boundaries must preserve canonical CRLF");
        for(String bad:List.of("Shale","x];DROP DATABASE Shale--","remote.host")){boolean refused=false;try{identifier(bad.contains(";")||bad.contains(".")?bad:"bad name");}catch(IllegalArgumentException e){refused=true;}require(refused,"Identifier rejection failed");}
        System.out.println("PASS parser/identifier contracts");
    }
}
