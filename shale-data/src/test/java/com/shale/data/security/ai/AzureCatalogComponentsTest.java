package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Offline: preserve the catalog contract, detect context differences, and reject incomplete evidence. */
class AzureCatalogComponentsTest {
    private static final List<String> PRIVATE_EVIDENCE=List.of("SELECT private_sql_text",
            "jdbc:sqlserver://private-target.invalid", "user=private_user;password=private_password",
            "SID=0x70726976617465", "token=private_token", "private driver message");
    @Test void resourceContainsOnlyTheExactOwnedSetAndNineteenUnchangedExpressions()throws Exception {
        String source=Files.readString(Path.of("src/main/resources/ai/confinement-v2-catalog.sql")).replace("\r\n","\n");
        String diagnostic=AzureCatalogComponents.sql().replace("\r\n","\n");
        String owned=source.substring(source.indexOf("DECLARE @Owned table"),source.indexOf("DECLARE @catalog nvarchar"));
        assertEquals(97,Pattern.compile("\\(N'").matcher(owned).results().count(),"Keep all 97 reviewed owned names");
        var expressions=Pattern.compile("^SET @catalog=@catalog\\+(.+);$",Pattern.MULTILINE).matcher(source).results().map(m->m.group(1)).toList();
        assertEquals(19,expressions.size());
        StringBuilder expected=new StringBuilder("-- Test-only read-only component metadata; expressions copied verbatim from the V2 catalog.\nSET NOCOUNT ON;\n")
                .append(owned).append("DECLARE @component nvarchar(max);\n");
        for(int i=0;i<19;i++)expected.append("SET @component=").append(expressions.get(i)).append(";\nSELECT ").append(i+1)
                .append(" AS ComponentOrdinal,N'").append(AzureCatalogComponents.NAMES.get(i))
                .append("' AS ComponentName,DATALENGTH(@component) AS ByteLength,CONVERT(varchar(64),HASHBYTES('SHA2_256',@component),2) AS Sha256;\n");
        assertTrue(expected.toString().equals(diagnostic),"Only local owned-name declarations, unchanged expressions, and sanitized metadata SELECTs are authorized");
        String installer=Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql")).replace("\r\n","\n");
        // Earlier installer prerequisite receipts have their own @catalog builders.
        // Compare only the final V2 receipt builder, preserving its concatenation order.
        String finalBuilder=installer.substring(installer.lastIndexOf("SET @catalog=N'';"));
        var installed=Pattern.compile("^SET @catalog=@catalog\\+(.+);$",Pattern.MULTILINE).matcher(finalBuilder).results().map(m->m.group(1)).toList();
        assertTrue(expressions.equals(installed),"Diagnostic must preserve install-time fields, filters, ordering, conversions and delimiters");
    }
    @Test void equalEmptyComponentsAreValidAndReportExactlyNineteenSanitizedRows() {
        var owner=rows();var verifier=rows();
        assertTrue(AzureCatalogComponents.differences(owner,verifier).isEmpty());
        var report=AzureCatalogComponents.report(owner,verifier);
        assertEquals(20,report.size());
        for(int i=1;i<report.size();i++)assertEquals(i+"\t"+AzureCatalogComponents.NAMES.get(i-1)+"\t0\t0\t"+"a".repeat(64)+"\t"+"a".repeat(64)+"\ttrue",report.get(i));
    }
    @Test void lengthAndDigestDifferencesIdentifyOnlyTheExactComponents() {
        var owner=rows();var verifier=rows();
        verifier.set(6,new AzureCatalogComponents.Component(2,"a".repeat(64)));
        verifier.set(10,new AzureCatalogComponents.Component(0,"b".repeat(64)));
        assertEquals(List.of("7:object_permissions","11:runtime_verifier_permissions"),AzureCatalogComponents.differences(owner,verifier));
        var report=AzureCatalogComponents.report(owner,verifier);
        assertEquals(2,report.stream().filter(line->line.endsWith("\tfalse")).count());
        assertThrows(IllegalStateException.class,()->AzureCatalogComponents.report(owner.subList(0,18),verifier));
    }
    @Test void malformedMetadataCannotBecomeEvidence() {
        for(String digest:Arrays.asList(null,"private SQL SID token","a".repeat(63))) {
            var failure=assertThrows(IllegalStateException.class,()->new AzureCatalogComponents.Component(0,digest));
            assertFalse(failure.toString().contains("private"));assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
        }
        assertThrows(IllegalStateException.class,()->new AzureCatalogComponents.Component(-1,"a".repeat(64)));
        assertEquals("a".repeat(64),new AzureCatalogComponents.Component(0,"A".repeat(64)).sha256());
    }
    @Test void jdbcDrainsExactlyNineteenResultsWithTheExistingOrdinaryQueryLimit()throws Exception {
        var closed=new ArrayList<String>();
        assertEquals(rows(),AzureCatalogComponents.read(connection(19,"valid",closed)));
        assertEquals(19,closed.stream().filter(x->x.equals("result")).count());assertEquals("statement",closed.getLast());
    }
    @Test void leadingAndInterleavedNoResultStatesDoNotHideComponentsOrExtraResults()throws Exception {
        for(int leading:List.of(1,4)) {
            var closed=new ArrayList<String>();
            assertEquals(rows(),AzureCatalogComponents.read(connection(19,"valid",closed,leading,true)));
            assertEquals(19,closed.stream().filter("result"::equals).count());
            assertEquals("statement",closed.getLast());
        }
        for(String mode:List.of("missing","extra","null","duplicate","order","name","update","zeroUpdate","lateUpdate","lateZeroUpdate")) {
            var closed=new ArrayList<String>();
            int count=mode.equals("missing")?18:mode.equals("extra")?20:19;
            var failure=assertThrows(IllegalStateException.class,()->AzureCatalogComponents.read(connection(count,mode,closed,4,true)),mode);
            assertSanitized(failure);
            assertEquals("statement",closed.getLast());
        }
    }
    @Test void leadingNoResultStatesAreTraversedBeforeTheFirstSelect()throws Exception {
        var closed=new ArrayList<String>();
        assertEquals(rows(),AzureCatalogComponents.read(connection(19,"valid",closed,4,false)));
        assertEquals(19,Collections.frequency(closed,"result"));assertEquals("statement",closed.getLast());
    }
    @Test void interleavedNoResultStatesAreTraversedBetweenSelects()throws Exception {
        var closed=new ArrayList<String>();
        assertEquals(rows(),AzureCatalogComponents.read(connection(19,"initialResult",closed,0,true)));
        assertEquals(19,Collections.frequency(closed,"result"));assertEquals("statement",closed.getLast());
    }
    @Test void terminalOrExcessiveNoResultStatesFailInFiniteTimeWithoutReexecutingSql() {
        for(int leading:List.of(0,AzureCatalogComponents.NON_RESULT_BUDGET+1)) {
            var closed=new ArrayList<String>();
            assertThrows(IllegalStateException.class,()->AzureCatalogComponents.read(connection(leading==0?0:19,"valid",closed,leading,false)));
            assertEquals(List.of("statement"),closed);
        }
    }
    @Test void missingExtraNullDuplicatedOrReorderedMetadataFailsClosedAndClosesJdbcResources() {
        for(String mode:List.of("missing","extra","null","duplicate","order","name","update")) {
            var closed=new ArrayList<String>();
            var failure=assertThrows(IllegalStateException.class,()->AzureCatalogComponents.read(connection(mode.equals("missing")?18:mode.equals("extra")?20:19,mode,closed)),mode);
            assertEquals("statement",closed.getLast());
            assertFalse(failure.toString().contains("private"));assertNull(failure.getCause());
        }
    }
    @Test void verifierOpenAndOwnerCloseFailuresCannotLeakThroughSurefireOrLeaveStaleEvidence()throws Exception {
        var output=Files.createTempFile("catalog-component-test-",".tsv");
        var logger=java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc");var prior=logger.getLevel();
        var opens=new int[2];var closes=new int[1];
        Connection owner=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()) {
            case "setNetworkTimeout"->null;
            case "getNetworkTimeout"->120_000;
            case "getMetaData"->Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(mp,mm,ma)->"12.6.1");
            case "close"->{closes[0]++;throw new SQLException("private close SQL SID token");}
            default->throw new AssertionError("No query before both connections open");
        });
        DataSource a=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,args)->{assertEquals("getConnection",m.getName());opens[0]++;return owner;});
        DataSource b=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,args)->{assertEquals("getConnection",m.getName());opens[1]++;throw new SQLException("private target password SQL SID token");});
        try {
            logger.setLevel(java.util.logging.Level.OFF);
            var failure=assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlCatalogComponentTest.compare(a,b,"private database","private user",output));
            assertTrue(failure.getMessage().contains("stage=VERIFIER_TARGET_PREFLIGHT"));
            assertFalse(Files.exists(output),"A failed attempt must not preserve a stale passing comparison");
            var bytes=new java.io.ByteArrayOutputStream();failure.printStackTrace(new java.io.PrintStream(bytes));
            assertFalse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("private"));
            assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
            assertArrayEquals(new int[]{1,1},opens);assertEquals(1,closes[0]);
        }finally{logger.setLevel(prior);Files.deleteIfExists(output);}
    }
    @Test void eachFailureStageIsFixedSanitizedAndLeavesNoComparisonEvidence()throws Exception {
        var expected=Map.of("ownerOpen","OWNER_TARGET_PREFLIGHT","ownerTarget","OWNER_TARGET_PREFLIGHT",
                "ownerClose","OWNER_TARGET_PREFLIGHT","verifierOpen","VERIFIER_TARGET_PREFLIGHT",
                "verifierTarget","VERIFIER_TARGET_PREFLIGHT","verifierClose","VERIFIER_TARGET_PREFLIGHT",
                "ownerRead","OWNER_COMPONENT_READ","verifierRead","VERIFIER_COMPONENT_READ",
                "ownerReadAndClose","OWNER_COMPONENT_READ","report","REPORT_WRITE");
        var logger=java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc");var prior=logger.getLevel();
        try {
            logger.setLevel(java.util.logging.Level.OFF);
            for(var scenario:expected.entrySet()) {
                var parent=Files.createTempDirectory("private-target-");var output=parent.resolve("report.tsv");
                var closed=new ArrayList<String>();
                try {
                    Files.writeString(output,"stale passing evidence");
                    var owner=diagnosticSource("owner",scenario.getKey(),closed,parent);
                    var verifier=diagnosticSource("verifier",scenario.getKey(),closed,parent);
                    var failure=assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlCatalogComponentTest.compare(owner,verifier,"private database","private user",output),scenario.getKey());
                    String component=scenario.getValue().endsWith("COMPONENT_READ")?"; component=0:before_first_component":"";
                    assertEquals("Catalog component diagnostic failed before a complete comparison; stage="+scenario.getValue()+component+"; discard fixture; no retry/adoption",failure.getMessage());
                    assertSanitized(failure);
                    assertFalse(Files.exists(output),"Failed comparisons must not leave stale or partial evidence");
                    assertEquals(scenario.getKey().equals("ownerOpen")?0:1,Collections.frequency(closed,"owner"));
                    assertEquals(List.of("ownerOpen","verifierOpen").contains(scenario.getKey())?0:1,Collections.frequency(closed,"verifier"));
                }finally{if(Files.isDirectory(parent))Files.deleteIfExists(output);Files.deleteIfExists(parent);}
            }
        }finally{logger.setLevel(prior);}
    }
    @Test void everyOwnerAndVerifierComponentFailureUsesOnlyItsFixedTraversalSlot()throws Exception {
        for(String role:List.of("owner","verifier"))for(int ordinal=1;ordinal<=19;ordinal++)
            for(String operation:List.of("getResultSet","next","getInt","getString","getLong","wasNull","close","getMoreResults")) {
                if(ordinal==1&&operation.equals("getMoreResults"))continue; // First execution has no known component.
                assertDiagnosticComponent(role,ordinal,operation);
            }
    }
    @Test void executeFailuresBeforeTheFirstComponentAreFixedAndSanitizedForBothIdentities()throws Exception {
        for(String role:List.of("owner","verifier"))assertDiagnosticComponent(role,0,"execute");
        var before=assertThrows(AzureCatalogComponents.ReadFailure.class,
                ()->AzureCatalogComponents.read(connection(19,"valid",new ArrayList<>(),4,true,2,"getMoreResults")));
        assertEquals("0:before_first_component",before.componentLabel());assertSanitized(before);
        var after=assertThrows(AzureCatalogComponents.ReadFailure.class,
                ()->AzureCatalogComponents.read(connection(19,"valid",new ArrayList<>(),0,false,0,"close")));
        assertEquals("19:word_transitions",after.componentLabel());assertSanitized(after);
    }
    @Test void malformedEvidenceHasOnlyExpectedAllowlistedLabels() {
        for(String mode:List.of("missing","extra","null","duplicate","order","name","update","zeroUpdate","lateUpdate","lateZeroUpdate")) {
            int count=mode.equals("missing")?18:mode.equals("extra")?20:19;
            var failure=assertThrows(AzureCatalogComponents.ReadFailure.class,
                    ()->AzureCatalogComponents.read(connection(count,mode,new ArrayList<>(),4,true)));
            int ordinal=List.of("update","zeroUpdate").contains(mode)?0:
                    List.of("missing","extra","lateUpdate","lateZeroUpdate").contains(mode)?19:1;
            assertEquals(ordinal==0?"0:before_first_component":ordinal+":"+AzureCatalogComponents.NAMES.get(ordinal-1),failure.componentLabel());
            assertSanitized(failure);
        }
    }
    private static void assertDiagnosticComponent(String role,int ordinal,String operation)throws Exception {
        var parent=Files.createTempDirectory("catalog-component-");var output=parent.resolve("report.tsv");
        var logger=java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc");var prior=logger.getLevel();
        try {
            logger.setLevel(java.util.logging.Level.OFF);
            Files.writeString(output,"stale passing evidence");
            String mode=role+"Read:"+ordinal+":"+operation;
            var closed=new ArrayList<String>();
            var failure=assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlCatalogComponentTest.compare(
                    diagnosticSource("owner",mode,closed,parent),diagnosticSource("verifier",mode,closed,parent),
                    "private database","private user",output),mode);
            String label=ordinal==0?"0:before_first_component":ordinal+":"+AzureCatalogComponents.NAMES.get(ordinal-1);
            assertEquals("Catalog component diagnostic failed before a complete comparison; stage="+role.toUpperCase(Locale.ROOT)+
                    "_COMPONENT_READ; component="+label+"; discard fixture; no retry/adoption",failure.getMessage(),mode);
            assertSanitized(failure);
            assertFalse(Files.exists(output));
            assertEquals(1,Collections.frequency(closed,"owner"));assertEquals(1,Collections.frequency(closed,"verifier"));
        }finally{logger.setLevel(prior);Files.deleteIfExists(output);Files.deleteIfExists(parent);}
    }
    private static DataSource diagnosticSource(String role,String mode,List<String> closed,Path parent) {
        int[] opens={0},statements={0};
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,a)->{
            assertEquals("getConnection",m.getName());assertEquals(1,++opens[0],"Never retry a failed diagnostic connection");
            if(mode.equals(role+"Open"))throw privateJdbcFailure();
            return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(cp,cm,ca)->switch(cm.getName()) {
                case "setNetworkTimeout"->null;
                case "getNetworkTimeout"->120_000;
                case "getMetaData"->Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(mp,mm,ma)->"12.6.1");
                case "createStatement"->{
                    if(++statements[0]==1)yield targetStatement(role,mode);
                    if(mode.equals(role+"Read")||mode.equals(role+"ReadAndClose"))throw privateJdbcFailure();
                    if(mode.startsWith(role+"Read:")) {
                        var fault=mode.split(":");
                        yield connection(19,"valid",new ArrayList<>(),0,false,Integer.parseInt(fault[1]),fault[2]).createStatement();
                    }
                    yield connection(19,"valid",new ArrayList<>(),4,true).createStatement();
                }
                case "close"->{
                    closed.add(role);
                    if(mode.equals(role+"Close")||mode.equals(role+"ReadAndClose")||mode.startsWith(role+"Read:"))throw privateJdbcFailure();
                    if(mode.equals("report")&&role.equals("verifier")) {
                        Files.delete(parent);Files.writeString(parent,"private report obstruction");
                    }
                    yield null;
                }
                default->throw new AssertionError("No transaction/session/authority change allowed");
            });
        });
    }
    private static Statement targetStatement(String role,String mode) {
        return (Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->switch(m.getName()) {
            case "setQueryTimeout"->{assertEquals(20,a[0]);yield null;}
            case "close"->null;
            case "executeQuery"->{
                assertTrue(((String)a[0]).startsWith("SELECT DB_NAME(),USER_NAME()"));
                if(mode.equals(role+"Target"))throw privateJdbcFailure();
                int[] visits={0};
                yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rp,rm,ra)->switch(rm.getName()) {
                    case "next"->++visits[0]==1;
                    case "getString"->switch((int)ra[0]){case 1->"private database";case 2->role.equals("owner")?"dbo":"private user";default->"private server";};
                    case "getInt"->(int)ra[0]==3?5:1;
                    case "close"->null;
                    default->throw new AssertionError("Unexpected preflight operation");
                });
            }
            default->throw new AssertionError("Only the existing read-only preflight is allowed");
        });
    }
    private static ArrayList<AzureCatalogComponents.Component> rows() {
        var result=new ArrayList<AzureCatalogComponents.Component>();
        for(int i=0;i<19;i++)result.add(new AzureCatalogComponents.Component(0,"a".repeat(64)));
        return result;
    }
    private static Connection connection(int count,String mode,List<String> closed) {
        return connection(count,mode,closed,0,false);
    }
    private static Connection connection(int count,String mode,List<String> closed,int leading,boolean interleaved) {
        return connection(count,mode,closed,leading,interleaved,-1,"");
    }
    private static Connection connection(int count,String mode,List<String> closed,int leading,boolean interleaved,int fault,String operation) {
        // State -1 is a real no-result observation, not a SELECT with empty rows.
        var states=new ArrayList<Integer>();
        for(int i=0;i<leading;i++)states.add(-1);
        for(int i=0;i<count;i++) {
            // The interleaved-only case starts with a SELECT to isolate gaps after result #1.
            if(interleaved&&(i>0||!mode.equals("initialResult")))states.add(-1);
            states.add(i);
        }
        int[] state={0},executions={0},advances={0};
        java.util.function.IntSupplier index=()->state[0]<states.size()?states.get(state[0]):-1;
        Statement statement=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->{
            if(m.getName().equals(operation)&&((operation.equals("execute")&&fault==0)||
                    (operation.equals("getMoreResults")&&state[0]+2==fault)||
                    (!operation.equals("getMoreResults")&&index.getAsInt()+1==fault)))throw privateJdbcFailure();
            return switch(m.getName()) {
            case "setQueryTimeout"->{assertEquals(20,a[0],"Diagnostic must use the unchanged ordinary owner query limit");yield null;}
            case "execute"->{assertEquals(1,++executions[0],"Never retry or reexecute the diagnostic");assertTrue(AzureCatalogComponents.sql().equals(a[0]),"Execute only the checked-in diagnostic resource");yield !List.of("update","zeroUpdate").contains(mode)&&index.getAsInt()>=0;}
            case "getUpdateCount"->mode.equals("update")||(mode.equals("lateUpdate")&&state[0]>=states.size())?1:
                    mode.equals("zeroUpdate")||(mode.equals("lateZeroUpdate")&&state[0]>=states.size())?0:-1;
            case "getResultSet"->{int[] visits={0};yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rp,rm,ra)->{
                if(index.getAsInt()+1==fault&&rm.getName().equals(operation))throw privateJdbcFailure();
                return switch(rm.getName()) {
                case "next"->++visits[0]<=(mode.equals("duplicate")?2:1);
                case "getInt"->mode.equals("order")?2:index.getAsInt()+1;
                case "getString"->(int)ra[0]==4?"a".repeat(64):mode.equals("name")?"private SQL SID token":AzureCatalogComponents.NAMES.get(Math.min(index.getAsInt(),18));
                case "getLong"->0L;
                case "wasNull"->mode.equals("null");
                case "close"->{closed.add("result");yield null;}
                default->throw new AssertionError("Unexpected result operation");
            };});}
            case "getMoreResults"->{
                assertEquals(Statement.CLOSE_CURRENT_RESULT,a[0]);
                assertTrue(++advances[0]<=500,"Terminal JDBC states must not spin indefinitely");
                state[0]++;yield index.getAsInt()>=0;
            }
            case "close"->{closed.add("statement");yield null;}
            default->throw new AssertionError("Unexpected statement operation");
        };});
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            assertEquals("createStatement",m.getName(),"Diagnostic may not change transactions, sessions or authority");return statement;
        });
    }
    private static void assertSanitized(Throwable failure) {
        var bytes=new java.io.ByteArrayOutputStream();failure.printStackTrace(new java.io.PrintStream(bytes));
        String rendered=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        for(String canary:PRIVATE_EVIDENCE)assertFalse(rendered.contains(canary),"Sanitized failures must not expose synthetic private evidence");
        assertFalse(rendered.contains("private"));
        assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
    }
    private static SQLException privateJdbcFailure() {
        String details=String.join(" | ",PRIVATE_EVIDENCE);
        var failure=new SQLException(details);
        failure.initCause(new IllegalStateException(details));
        failure.addSuppressed(new SQLException(details));
        return failure;
    }
}
