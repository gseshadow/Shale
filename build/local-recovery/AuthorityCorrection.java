import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Existing-state LOCAL correction. No retirement, application rows, credentials or cleanup. */
public final class AuthorityCorrection {
    static final com.google.gson.Gson JSON = new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final String ORIGINAL = "0xC584A399EA216FF10DD0E3C28AEDC8CC306DDB42606C93009F4C75123DC502AF";
    static final Set<String> LEGACY = Set.of("[dbo].[sp_login_get_hash]","[dbo].[sp_login_mark_failure]","[dbo].[sp_login_mark_success]");
    static String expand(Path root,Path file)throws Exception {
        var out=new StringBuilder();
        for(String line:Files.readAllLines(file)) {
            if(line.startsWith(":r "))out.append(expand(root,root.resolve(line.substring(3).trim())));
            else if(!line.startsWith(":ON ERROR"))out.append(line).append('\n');
        }
        return out.toString();
    }
    static List<Map<String,String>> rows(Connection c,String query)throws Exception {
        var rows=new ArrayList<Map<String,String>>();
        try(var q=c.createStatement();var r=q.executeQuery(query)) {
            while(r.next()) {var row=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));rows.add(row);}
        }return rows;
    }
    static List<Map<String,String>> script(Connection c,Path root,String relative,Map<String,String> vars)throws Exception {
        return script(c,root,relative,vars,new ArrayList<>());
    }
    static List<Map<String,String>> script(Connection c,Path root,String relative,Map<String,String> vars,List<Map<String,String>> evidence)throws Exception {
        String text=expand(root,root.resolve(relative));
        for(var v:vars.entrySet())text=text.replace("$("+v.getKey()+")",v.getValue());
        Sandbox.require(!text.contains("$("),"Missing explicit SQL variable");
        try {
            for(String batch:Sandbox.batches(text))if(!Sandbox.clean(batch).isBlank())try(var q=c.createStatement()) {
                q.setQueryTimeout(20);boolean result=q.execute(batch);
                while(result||q.getUpdateCount()!=-1) {
                    if(result)try(var r=q.getResultSet()) {while(r.next()){var row=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));evidence.add(row);}}
                    result=q.getMoreResults();
                }
            }
        }catch(Exception e){Sandbox.sql(c,"IF @@TRANCOUNT>0 ROLLBACK; IF USER_NAME()<>N'dbo' REVERT;");throw e;}
        return evidence;
    }
    static void originalOwner(Connection c,String db,String nonce)throws Exception {
        Sandbox.checkLaunchOwner(c,db,nonce);
    }
    static String baseline(Connection c,Path root,String db)throws Exception {
        var result=script(c,root,"docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql",Map.of("ExpectedDatabase",db));
        return result.stream().filter(r->"LegacyRetirementBaseline".equals(r.get("CheckName"))).findFirst().orElseThrow().get("StateHash");
    }
    static List<Map<String,String>> diagnostics(Connection c,Path root)throws Exception {
        return diagnostics(c,root,new ArrayList<>());
    }
    static List<Map<String,String>> diagnostics(Connection c,Path root,List<Map<String,String>> fullOutput)throws Exception {
        script(c,root,"docs/sql/auth/permission-cutover/auth-authority-snapshot.sql",Map.of());
        script(c,root,"docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql",Map.of(),fullOutput);
        // Unlike the bounded display, retain EVERY check, including all NULLs and all findings.
        return rows(c,"SELECT * FROM #AuthAuthorityChecks ORDER BY ConditionName,Securable,PermissionName");
    }
    static void expectedFindings(List<Map<String,String>> checks,boolean corrected) {
        var found=new HashSet<String>();int count=0;
        for(var r:checks)if(r.get("EffectiveValue")==null||r.get("EffectiveValue").equals("1")) {
            count++;
            Sandbox.require("1".equals(r.get("EffectiveValue")),"UNKNOWN authority blocks correction");
            String key=r.get("ConditionName")+"|"+r.get("Securable")+"|"+r.get("PermissionName");
            found.add(key);
        }
        var expected=new HashSet<String>();for(String name:LEGACY)expected.add("object|"+name+"|EXECUTE");
        if(!corrected){expected.add("object|[sandbox].[Ownership]|SELECT");expected.add("column|[sandbox].[Ownership].[Nonce]|SELECT");}
        Sandbox.require(count==expected.size()&&found.equals(expected),"Complete authority findings differ; review ALL local blockers; no correction");
    }
    static void clear(Connection c)throws Exception {
        for(String table:List.of("LegacyTargets","LegacyFingerprint","AuthAuthorityChecks","CutoverPrincipals","CutoverObjects","CutoverColumns","CutoverOwnership","CutoverGrantOptions","CutoverVisibility"))Sandbox.sql(c,"DROP TABLE IF EXISTS #"+table);
    }
    public static void main(String[] args) {
        try { executeMain(args); }
        catch(Exception failure) {
            System.err.println("STOP local ownership correction: "+failure.getClass().getSimpleName()+"; preserve all evidence; no retry.");
            if(failure instanceof SQLException q)System.err.println("SQL error number="+q.getErrorCode());
            else if(failure instanceof IllegalArgumentException)System.err.println(failure.getMessage());
            System.exit(1);
        }
    }
    static void executeMain(String[] args)throws Exception {
        Sandbox.require(args.length==4,"Usage: AuthorityCorrection inspect|correct|check state tools update");
        var s=new Sandbox(Path.of(args[1]),Path.of(args[2]));Path update=Path.of(args[3]);
        Path root=s.tools.getParent().getParent();String db=s.p.getProperty("database"),nonce=s.p.getProperty("nonce");
        Sandbox.require(db.equals("ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c")&&s.p.getProperty("sqlPort").equals("14333"),"Only the reviewed existing Windows state is supported");
        var evidence=new LinkedHashMap<String,Object>();
        if(args[0].equals("inspect")) {
            // Create-new before connecting: failed inspections are preserved, never overwritten.
            Path log=update.resolve("inspection.json");Files.createFile(log);
            try(var c=s.admin(db)) {
                originalOwner(c,db,nonce);String before=baseline(c,root,db);clear(c);
                evidence.put("BeforeHash",before);
                var diagnosticOutput=new ArrayList<Map<String,String>>();
                var checks=diagnostics(c,root,diagnosticOutput);evidence.put("Checks",checks);
                evidence.put("DiagnosticOutput",diagnosticOutput);
                evidence.put("AuthToken",token(c,"shale_app"));
                evidence.put("RuntimeToken",token(c,"shale_runtime"));
                evidence.put("RuntimeChecks",runtimeChecks(c));
                Files.writeString(log,JSON.toJson(evidence));
                Sandbox.require(ORIGINAL.equalsIgnoreCase(before),"Original failed-attempt baseline drift; stop");
                expectedFindings(checks,false);
                runtimeCheck(c);
            }
            System.out.println("INSPECTED complete auth checks; original catalog unchanged. Independently review inspection.json and failed Apply diagnostics before CorrectAuthority.");
            return;
        }
        if(args[0].equals("correct")) {
            Files.writeString(update.resolve("correction-attempted"),java.time.Instant.now().toString(),StandardOpenOption.CREATE_NEW);
            try(var c=s.admin(db)) {
                originalOwner(c,db,nonce);Sandbox.require(ORIGINAL.equalsIgnoreCase(baseline(c,root,db)),"Original baseline drift");clear(c);
                expectedFindings(diagnostics(c,root),false);runtimeCheck(c);clear(c);
                loginChecks(s);
                evidence.put("BeforeHash",ORIGINAL);
                evidence.put("SqlResults",script(c,root,"build/local-recovery/correct-authority.sql",Map.of("ExpectedDatabase",db,"OwnershipNonce",nonce,"LegacyBeforeHash",ORIGINAL)));
                clear(c);evidence.put("CorrectedBaseline",baseline(c,root,db));
                Files.writeString(update.resolve("correction-result.json"),JSON.toJson(evidence),StandardOpenOption.CREATE_NEW);
            }
        }else Sandbox.require(args[0].equals("check"),"Unknown correction phase");
        try(var c=s.admin(db)){originalOwner(c,db,nonce);}
        loginChecks(s);
        System.out.println("PASS corrected ownership and actual login/non-bypass launch preconditions; no application trial or manual recovery measured");
    }
    static void loginChecks(Sandbox s)throws Exception {
        String db=s.p.getProperty("database");
        // Real login connections, not dbo impersonation. Also checked BEFORE the correction.
        for(String role:List.of("app","runtime"))try(var c=s.connect(db,s.p.getProperty(role.equals("app")?"appLogin":"runtimeLogin"),Sandbox.env(role.equals("app")?"SHALE_SANDBOX_APP_PASSWORD":"SHALE_SANDBOX_RUNTIME_PASSWORD"))) {
            if(role.equals("runtime"))s.owned(c);
            Sandbox.launchIdentity(c,db,role.equals("app")?"shale_app":"shale_runtime",s.p.getProperty(role.equals("app")?"appLogin":"runtimeLogin"));
        }
    }
    static List<Map<String,String>> token(Connection c,String user)throws Exception {
        Sandbox.sql(c,"EXECUTE AS USER="+Sandbox.literal(user));
        try{return rows(c,"SELECT p.name AS PrincipalName,t.usage AS TokenUsage FROM sys.user_token t JOIN #CutoverPrincipals p ON p.principal_id=t.principal_id ORDER BY p.name");}finally{Sandbox.sql(c,"REVERT");}
    }
    static void runtimeCheck(Connection c)throws Exception { requireRuntimeChecks(runtimeChecks(c)); }
    static void requireRuntimeChecks(List<Map<String,String>> checks) {
        Sandbox.require(checks.stream().allMatch(r->"0".equals(r.get("EffectiveValue"))),"Runtime escalation/UNKNOWN findings; inspect ALL RuntimeChecks before proceeding");
    }
    static List<Map<String,String>> runtimeChecks(Connection c)throws Exception {
        var elevated=rows(c,"SELECT QUOTENAME(OBJECT_SCHEMA_NAME(m.object_id))+N'.'+QUOTENAME(OBJECT_NAME(m.object_id)) AS Securable FROM sys.sql_modules m WHERE execute_as_principal_id IS NOT NULL OR definition IS NULL OR EXISTS(SELECT 1 FROM sys.crypt_properties cp WHERE cp.class=1 AND cp.major_id=m.object_id)");
        var result=new ArrayList<Map<String,String>>();
        Sandbox.sql(c,"EXECUTE AS USER=N'shale_runtime'");
        try {
            result.addAll(rows(c,"SELECT N'membership' AS ConditionName,N'shale_runtime_role' AS Securable,CASE WHEN IS_MEMBER(N'shale_runtime_role')=1 THEN 0 ELSE 1 END AS EffectiveValue"));
            result.addAll(rows(c,"SELECT N'fixed-role' AS ConditionName,p.name AS Securable,1 AS EffectiveValue FROM sys.user_token t JOIN #CutoverPrincipals p ON p.principal_id=t.principal_id WHERE p.is_fixed_role=1 AND p.name<>N'public'"));
            result.addAll(rows(c,"SELECT N'ownership' AS ConditionName,Securable,1 AS EffectiveValue FROM #CutoverOwnership WHERE principal_id IN(SELECT principal_id FROM sys.user_token)"));
            result.addAll(rows(c,"SELECT N'impersonate' AS ConditionName,name AS Securable,HAS_PERMS_BY_NAME(name,'USER','IMPERSONATE') AS EffectiveValue FROM #CutoverPrincipals WHERE type IN('S','E','X') AND name<>USER_NAME()"));
            for(String permission:List.of("CONTROL","ALTER ANY ROLE","ALTER ANY USER","ALTER ANY SCHEMA","ALTER ANY SECURITY POLICY"))result.addAll(rows(c,"SELECT N'database' AS ConditionName,"+Sandbox.literal(permission)+" AS Securable,HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE',"+Sandbox.literal(permission)+") AS EffectiveValue"));
            result.addAll(rows(c,"SELECT N'ShaleAdmins' AS ConditionName,N'ShaleAdmins' AS Securable,CASE WHEN EXISTS(SELECT 1 FROM #CutoverPrincipals WHERE name=N'ShaleAdmins') THEN IS_MEMBER(N'ShaleAdmins') ELSE 0 END AS EffectiveValue"));
            for(String permission:List.of("ALTER","CONTROL"))result.addAll(rows(c,"SELECT "+Sandbox.literal(permission)+" AS ConditionName,Securable,HAS_PERMS_BY_NAME(Securable,'OBJECT',"+Sandbox.literal(permission)+") AS EffectiveValue FROM #CutoverObjects"));
            for(var row:elevated)result.addAll(rows(c,"SELECT N'elevated-execute' AS ConditionName,"+Sandbox.literal(row.get("Securable"))+" AS Securable,HAS_PERMS_BY_NAME("+Sandbox.literal(row.get("Securable"))+",'OBJECT','EXECUTE') AS EffectiveValue"));
        }finally{Sandbox.sql(c,"REVERT");}
        return result;
    }
}
