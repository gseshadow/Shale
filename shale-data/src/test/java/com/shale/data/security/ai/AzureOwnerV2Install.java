package com.shale.data.security.ai;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable owner harness only. Original migration/module bytes and receipts stay frozen. */
final class AzureOwnerV2Install {
    private AzureOwnerV2Install() {}
    static final String RAW_SHA256="7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8";
    record Plan(String sql, List<String> stages) {}

    static Plan diagnosticPlan(byte[] raw) {
        if(!RAW_SHA256.equals(HexFormat.of().formatHex(AiSql.hash(raw))))
            throw new IllegalStateException("Reviewed LF V2 installer bytes required");
        StringBuilder sql=new StringBuilder();var stages=new ArrayList<String>();int rows=0;
        for(String line:new String(raw,StandardCharsets.UTF_8).split("\n",-1)) {
            String category=null;
            if(line.startsWith("SET NOCOUNT ON; SET XACT_ABORT"))category="SESSION";
            else if(line.startsWith("DECLARE @ReviewedCaseModules"))category="CASE_MODULES";
            else if(line.startsWith("DECLARE @caseObjects")||line.startsWith("SET @caseCatalog="))category="CASE_CATALOG";
            else if(line.startsWith("DECLARE @Owned")||line.startsWith("SET @catalog="))category="AI_CATALOG";
            else if(line.startsWith("CREATE "))category="DDL";
            else if(line.startsWith("INSERT dbo.AiData")&&rows++%256==0)category="UNICODE";
            else if(line.startsWith("SET @InstallDefinition="))category="MODULE";
            else if(line.startsWith("EXEC(N'ALTER SECURITY POLICY"))category="RLS_AND_GRANTS";
            else if(line.startsWith("DECLARE @hash"))category="RECEIPTS";
            else if(line.equals("COMMIT TRANSACTION;"))category="COMMIT";
            if(category!=null) {
                stages.add(category);sql.append(checkpoint(stages.size()));
            }
            sql.append(line).append('\n');
        }
        // split preserves the final empty line; remove only our extra terminator.
        sql.setLength(sql.length()-1);
        return new Plan(sql.toString(),List.copyOf(stages));
    }
    static String checkpoint(int stage) {return "RAISERROR(N'AZURE_V2_CHECKPOINT_"+stage+"',10,1) WITH NOWAIT;\n";}

    static void install(Connection c,byte[] raw)throws SQLException {
        execute(c,diagnosticPlan(raw),AiCaseDataAzureSqlDisposableSetupTest.OWNER_QUERY_SECONDS);
    }
    // Separate entry point permits constrained synthetic local cancellation tests, never an Azure opt-in.
    static void execute(Connection c,Plan plan,int seconds)throws SQLException {
        long started=System.nanoTime();int last=0;
        try {
            try(var statement=c.createStatement()) {
                statement.setQueryTimeout(seconds);
                // Adaptive buffering ends the driver's query timer at the first TDS packet.
                // NOWAIT checkpoints must never extend this disposable install's wall bound.
                try(var deadline=new Deadline(c,statement,seconds)) {
                  try {
                    boolean result=statement.execute(plan.sql());
                    while(true) {
                        if(result)try(var r=statement.getResultSet()){while(r.next()){} }
                        else if(statement.getUpdateCount()==-1)break;
                        result=statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                    }
                    if(deadline.expired.get())throw new SQLTimeoutException("V2 execution deadline reached");
                  } finally {
                    // Warnings can contain arbitrary SQL/target text. Compare exact fixed tokens;
                    // never print, chain or stringify the warning or any unrecognized message.
                    try {last=lastCheckpoint(statement.getWarnings(),plan.stages().size());}
                    catch(SQLException ignored){/* Closed transport: no checkpoint evidence. */}
                    System.err.printf(Locale.ROOT,"AZURE_V2 lastObserved=%d category=%s elapsedMillis=%d querySeconds=%d%n",
                            last,last==0?"UNOBSERVED":plan.stages().get(last-1),(System.nanoTime()-started)/1_000_000,seconds);
                  }
                }
            }
            requireCleanTransaction(c);
        } catch(SQLException|RuntimeException failure) {
            String state=failure instanceof SQLException sql?sql.getSQLState():null;
            String safeState=state!=null&&state.matches("[A-Z0-9]{5}")?state:"unavailable";
            int code=failure instanceof SQLException sql?sql.getErrorCode():0;
            System.err.printf(Locale.ROOT,"AZURE_V2 failure=%s sqlState=%s sqlCode=%d%n",
                    failure instanceof SQLTimeoutException?"QUERY_TIMEOUT":"EXECUTION_FAILURE",safeState,code);
            boolean confirmed=false;
            try {rollbackAndVerifyAbsent(c);confirmed=true;}catch(SQLException|RuntimeException ignored){/* Private details never escape. */}
            System.err.println("AZURE_V2 rollback="+(confirmed?"CONFIRMED":"UNCONFIRMED")+" discardRequired=true");
            // No cause/suppressed driver chain in Surefire, including statement-close failures.
            throw new SQLException("V2 install failed; lastObserved="+last+"; rollback="+(confirmed?"CONFIRMED":"UNCONFIRMED")+"; discard fixture; no retry/adoption");
        }
    }
    private static final class Deadline implements AutoCloseable {
        final AtomicBoolean expired=new AtomicBoolean(),finished=new AtomicBoolean();
        final ScheduledThreadPoolExecutor timer;
        Deadline(Connection c,Statement statement,int seconds) {
            timer=new ScheduledThreadPoolExecutor(2,task->{var thread=new Thread(task,"disposable-v2-deadline");thread.setDaemon(true);return thread;});
            timer.schedule(()->{
                if(!finished.get()) {
                    expired.set(true);
                    try{statement.cancel();}catch(SQLException|RuntimeException ignored){/* Close fallback remains scheduled. */}
                }
            },seconds,TimeUnit.SECONDS);
            timer.schedule(()->{
                if(!finished.get())try{c.close();}catch(SQLException|RuntimeException ignored){/* No private driver text. */}
            },seconds+AiCaseDataAzureSqlDisposableSetupTest.OWNER_CANCEL_SECONDS,TimeUnit.SECONDS);
        }
        @Override public void close(){finished.set(true);timer.shutdownNow();}
    }
    static int lastCheckpoint(SQLWarning warning,int count) {
        int last=0;
        for(int visited=0;warning!=null&&visited<1024;visited++,warning=warning.getNextWarning())
            for(int i=last+1;i<=count;i++)if(("AZURE_V2_CHECKPOINT_"+i).equals(warning.getMessage())){last=i;break;}
        return last;
    }
    private static void requireCleanTransaction(Connection c)throws SQLException {
        try(var s=c.createStatement()) {
            s.setQueryTimeout(20);
            try(var r=s.executeQuery("SELECT @@TRANCOUNT,XACT_STATE()")) {
                if(!r.next()||r.getInt(1)!=0||r.getInt(2)!=0||r.next())throw new SQLException("V2 transaction not closed");
            }
        }
    }
    static void rollbackAndVerifyAbsent(Connection c)throws SQLException {
        // JDBC rollback() refuses autoCommit=true, even when T-SQL opened a transaction.
        try(var s=c.createStatement()) {
            s.setQueryTimeout(20);
            s.execute("IF XACT_STATE()<>0 ROLLBACK TRANSACTION");
        }
        requireCleanTransaction(c);
        try(var s=c.createStatement()) {
            s.setQueryTimeout(20);
            try(var r=s.executeQuery("SELECT (SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0 AND (name LIKE N'AiData%' OR name LIKE N'AiCaseData%'))+(SELECT COUNT(*) FROM sys.types WHERE is_user_defined=1 AND name=N'AiCaseDataIds')+(SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'AiCaseDataRuntime',N'AiCaseDataCatalogVerifier'))+(SELECT COUNT(*) FROM sys.indexes WHERE name=N'UX_AiDataCredentialOwner')+(SELECT COUNT(*) FROM sys.extended_properties WHERE name IN(N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source'))")) {
                if(!r.next()||r.getInt(1)!=0||r.next())throw new SQLException("V2 absence unconfirmed");
            }
        }
    }
}
