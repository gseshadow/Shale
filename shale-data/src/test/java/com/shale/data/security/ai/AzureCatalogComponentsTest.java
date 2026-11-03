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
            assertFalse(Files.exists(output),"A failed attempt must not preserve a stale passing comparison");
            var bytes=new java.io.ByteArrayOutputStream();failure.printStackTrace(new java.io.PrintStream(bytes));
            assertFalse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("private"));
            assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
            assertArrayEquals(new int[]{1,1},opens);assertEquals(1,closes[0]);
        }finally{logger.setLevel(prior);Files.deleteIfExists(output);}
    }
    private static ArrayList<AzureCatalogComponents.Component> rows() {
        var result=new ArrayList<AzureCatalogComponents.Component>();
        for(int i=0;i<19;i++)result.add(new AzureCatalogComponents.Component(0,"a".repeat(64)));
        return result;
    }
    private static Connection connection(int count,String mode,List<String> closed) {
        int[] index={0};
        Statement statement=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->switch(m.getName()) {
            case "setQueryTimeout"->{assertEquals(20,a[0],"Diagnostic must use the unchanged ordinary owner query limit");yield null;}
            case "execute"->{assertTrue(AzureCatalogComponents.sql().equals(a[0]),"Execute only the checked-in diagnostic resource");yield !mode.equals("update");}
            case "getUpdateCount"->mode.equals("update")?1:-1;
            case "getResultSet"->{int[] visits={0};yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rp,rm,ra)->switch(rm.getName()) {
                case "next"->++visits[0]<=(mode.equals("duplicate")?2:1);
                case "getInt"->mode.equals("order")?2:index[0]+1;
                case "getString"->(int)ra[0]==4?"a".repeat(64):mode.equals("name")?"private SQL SID token":AzureCatalogComponents.NAMES.get(Math.min(index[0],18));
                case "getLong"->0L;
                case "wasNull"->mode.equals("null");
                case "close"->{closed.add("result");yield null;}
                default->throw new AssertionError("Unexpected result operation");
            });}
            case "getMoreResults"->{assertEquals(Statement.CLOSE_CURRENT_RESULT,a[0]);yield ++index[0]<count;}
            case "close"->{closed.add("statement");yield null;}
            default->throw new AssertionError("Unexpected statement operation");
        });
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            assertEquals("createStatement",m.getName(),"Diagnostic may not change transactions, sessions or authority");return statement;
        });
    }
}
