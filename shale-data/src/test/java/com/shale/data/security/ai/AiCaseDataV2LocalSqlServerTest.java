package com.shale.data.security.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inherits the unchanged authority/parity/fault assertions; explicitly installs and attests V2.
 * Production metadata is private hash-gated input. All SQL executes on fresh disposable loopback DBs. */
class AiCaseDataV2LocalSqlServerTest extends AiCaseDataLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V2;}
    @Override String confinementMigration(){return "phase3b8-confinement.sql";}
    @Override String confinementPrefix(){return "confinement-v2";}
    boolean productionVariant(){return true;}
    @Override String confinementText()throws Exception {
        String text=super.confinementText();
        return productionVariant()?text.replace("\r\n","\n").replace("\n","\r\n"):text;
    }

    @Override List<String> fixtureSchema(){
        if(!productionVariant())return schema;
        try {
            Map<String,String> modules=productionFunctions();
            var batches=new ArrayList<String>();
            for(String original:schema){
                String batch=original;
                for(String name:modules.keySet())if(batch.contains("FUNCTION ["+name.replace(".","].[" )+"]"))batch=modules.get(name);
                if(batch.contains("CREATE TABLE [dbo].[Cases]")){
                    assertTrue(batch.contains("\t[CaseNumber]"));assertTrue(batch.contains("\t[Summary]"));
                    batch=batch.replace("\t[CaseNumber]","[V2Gap9] int NULL,\r\n\t[CaseNumber]")
                            .replace("\t[Summary]","[V2Gap36] int NULL,[V2Gap37] int NULL,\r\n\t[Summary]");
                }
                batches.add(batch);
            }
            return batches;
        }catch(Exception e){throw new IllegalStateException("Private production evidence unavailable or invalid",e);}
    }
    @Override void afterSchema(Connection c)throws Exception {
        if(productionVariant())sql(c,"ALTER TABLE dbo.Cases DROP COLUMN V2Gap9,V2Gap36,V2Gap37");
    }
    @Override String prerequisiteText(String migration)throws Exception {
        String sql=super.prerequisiteText(migration);
        return productionVariant()?sql.replace("\r\n","\n").replace("\n","\r\n"):sql;
    }
    @Override void beforeConfinement(Connection c)throws Exception {
        for(String target:List.of("Users","auditLog","Statuses"))sql(c,"ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo."+target);
        if(productionVariant())assertHistoricalHash(c,"EB39DDBFA59DF338DCAA969E440C7D254B1CB27ACE1211524375D786B2006AB1");
        String prerequisite=Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql"));
        assertThrows(SQLException.class,()->sql(c,prerequisite),"Current production without Phase1R must fail V2");
        String engine=Files.readString(Path.of("../docs/sql/phase1r/engine.sql"));
        Map<String,String> reviewed=new LinkedHashMap<>();reviewed.put("ExpectedDatabase",db);reviewed.put("ReviewedCollation","SQL_Latin1_General_CP1_CI_AS");
        for(var entry:Map.of("ReviewedStrictHash","sec.fn_FilterByTenant","ReviewedOverlayHash","sec.fn_FilterByTenantOrGlobal").entrySet()){
            try(var st=c.createStatement();var rs=st.executeQuery("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID(N'"+entry.getValue()+"'))),2)")){
                assertTrue(rs.next());reviewed.put(entry.getKey(),rs.getString(1));
            }
        }
        try(var st=c.createStatement();var rs=st.executeQuery("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT p.* FROM sys.security_predicates p WHERE p.target_object_id NOT IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')) ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)),2)")){
            assertTrue(rs.next());reviewed.put("ReviewedOtherPredicatesHash",rs.getString(1));
        }
        // These hashes describe only the reviewed, freshly constructed local fixture; never live adoption.
        for(var entry:reviewed.entrySet())engine=engine.replace("$("+entry.getKey()+")",entry.getValue());
        for(String mode:List.of("PREFLIGHT","APPLY","POSTCHECK"))sql(c,"DECLARE @Phase1RMode varchar(10)='"+mode+"';\n"+engine);
        assertEquals(1,count(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE class=3 AND major_id=SCHEMA_ID('sec') AND name='Shale.Phase1R.v1'"));
        sql(c,prerequisite);
        if(productionVariant())assertHistoricalHash(c,"D4AA4B9D029044A69BA0FE10A171DD6B5447374EC3124CBFB6EA6E743E49F0CC");
        // Historical V1 must still reject the production representation, without installing anything.
        if(productionVariant())assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql"))));
    }

    @Test void v2ReceiptAndCumulativeVerifierAreExclusive()throws Exception {
        try(var c=owner(db)){
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));
            Path receipts=Path.of("../work/confinement-v2");Files.createDirectories(receipts);
            try(var st=c.createStatement();var rs=st.executeQuery("SELECT name,CONVERT(varchar(64),CONVERT(varbinary(32),value),2) FROM sys.extended_properties WHERE major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name IN('Shale.AiCaseDataV2','Shale.AiCaseDataV2.Source') ORDER BY name")){
                var lines=new ArrayList<String>();while(rs.next())lines.add(rs.getString(1)+"="+rs.getString(2));
                Files.write(receipts.resolve((productionVariant()?"production":"authoritative")+"-receipt.txt"),lines);
            }
            assertEquals(2,count(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name IN('Shale.AiCaseDataV2','Shale.AiCaseDataV2.Source')"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name LIKE 'Shale.AiCaseDataV1%'"));
        }
        assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(source("runtime")),db,ISSUER,RESOURCE,false,AiCaseDataAttestation.Contract.V1).verify());
        attestation().verify();
    }
    @Test void semanticColumnMutationsNeverBecomeRepresentationVariants()throws Exception {
        for(String mutation:List.of(
                "ALTER TABLE dbo.Cases ALTER COLUMN Summary nvarchar(200) NULL",
                "UPDATE dbo.Cases SET Summary=N''; ALTER TABLE dbo.Cases ALTER COLUMN Summary nvarchar(max) NOT NULL",
                "ALTER TABLE dbo.Cases ALTER COLUMN Summary nvarchar(max) COLLATE Latin1_General_100_BIN2 NULL",
                "DROP INDEX IX_Users_email_norm ON dbo.Users; ALTER TABLE dbo.Users DROP COLUMN email_norm; ALTER TABLE dbo.Users ADD email_norm AS (upper([email])) PERSISTED",
                "ALTER TABLE dbo.Cases ADD V2Unexpected int NULL",
                "ALTER TABLE dbo.Cases DROP COLUMN Summary",
                "ALTER TABLE dbo.Cases DROP COLUMN Summary; ALTER TABLE dbo.Cases ADD Summary nvarchar(max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL"))rejectCatalogMutation(mutation);
    }
    @Test void rlsAndOwnershipMutationsRemainClosed()throws Exception {
        for(String mutation:List.of(
                "ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=OFF)",
                "ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users",
                "ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users; EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.Users')",
                "ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users AFTER INSERT",
                "ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users; EXEC(N'CREATE SECURITY POLICY sec.V2Conflicting ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users WITH(STATE=ON,SCHEMABINDING=ON)')",
                "ALTER AUTHORIZATION ON OBJECT::dbo.ToBitNV TO ["+prefix+"_runtime]"))rejectCatalogMutation(mutation);
    }
    @Test void syntaxLookingLiteralChangesAreNotNormalized()throws Exception {
        String definition=productionFunctions().get("dbo.ToBitNV");
        if(!productionVariant()){
            try(var c=owner(db);var st=c.createStatement();var rs=st.executeQuery("SELECT OBJECT_DEFINITION(OBJECT_ID('dbo.ToBitNV'))")){assertTrue(rs.next());definition=rs.getString(1);}
        }
        for(String replacement:List.of("'[N/A]'","'N/A  '","'N/\r\nA'","'NULL] ; --'")){
            String changed=definition.replaceFirst("CREATE FUNCTION","ALTER FUNCTION").replace("'N/A'",replacement);
            assertNotEquals(definition,changed);rejectCatalogMutation(changed);
        }
    }
    @Test void changedRlsModuleSemanticsAndSettingsAreRejectedBeforeCoverage()throws Exception {
        String original;
        try(var c=owner(db);var st=c.createStatement();var rs=st.executeQuery("SELECT OBJECT_DEFINITION(OBJECT_ID('sec.fn_FilterByTenant'))")){assertTrue(rs.next());original=rs.getString(1);}
        for(String changed:List.of(original.replace("N'ShaleClientId'","N'PrincipalUserId'"),
                original.replace("TRY_CONVERT(INT,","TRY_CONVERT(BIGINT,"),
                original.replace("WITH SCHEMABINDING", ""),
                original.replace("RETURNS TABLE", "RETURNS TABLE WITH EXECUTE AS OWNER").replace(" WITH SCHEMABINDING", ""),
                original.replace("SELECT 1 AS allowed", "SELECT 1 AS allowed FROM dbo.ShaleClients"))){
            // Remove schema-bound callers in this rolled-back local fault only, so ALTER really executes.
            try(var c=owner(db)){
                sql(c,"BEGIN TRANSACTION");
                try{
                    sql(c,"DROP SECURITY POLICY sec.TenantFilter");
                    String altered=changed.replaceFirst("CREATE\\s+FUNCTION","ALTER FUNCTION");
                    // Inline TVFs cannot EXECUTE AS; SQL's own rejection is also required for that invalid form.
                    if(changed.contains("EXECUTE AS OWNER"))assertThrows(SQLException.class,()->sql(c,altered));
                    else {sql(c,altered);SQLException failure=assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql"))));assertTrue(failure.getMessage().contains("not an approved representation"));}
                }finally{sql(c,"IF XACT_STATE()<>0 ROLLBACK");}
            }
        }
    }
    private void rejectCatalogMutation(String mutation)throws Exception {
        String prerequisite=Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql"));
        try(var c=owner(db)){
            sql(c,"BEGIN TRANSACTION");
            try{sql(c,mutation);assertThrows(SQLException.class,()->sql(c,prerequisite),mutation);}
            finally{sql(c,"IF XACT_STATE()<>0 ROLLBACK");}
            sql(c,prerequisite);
        }
    }
    private void assertHistoricalHash(Connection c,String expected)throws Exception {
        try(var st=c.createStatement();var rs=st.executeQuery(Files.readString(Path.of("../build/sql-confinement/case-catalog.sql")))){
            assertTrue(rs.next());assertEquals(expected,rs.getString(1));assertFalse(rs.next());
        }
    }
    static Map<String,String> productionFunctions()throws Exception {
        String path=System.getenv("SHALE_V2_PRODUCTION_CATALOG");assertNotNull(path,"Private exact production evidence is required for V2 projection acceptance");
        var parts=new LinkedHashMap<String,byte[]>();
        for(String line:Files.readAllLines(Path.of(path))){
            if(line.isBlank())continue;String[] fields=line.split("\\|",5);assertEquals(5,fields.length);
            byte[] bytes=Base64.getDecoder().decode(fields[4]);assertEquals(Integer.parseInt(fields[3]),bytes.length);assertEquals(Integer.parseInt(fields[2])*2,bytes.length);
            assertEquals(fields[1].toLowerCase(Locale.ROOT),HexFormat.of().formatHex(AiSql.hash(bytes)));assertNull(parts.put(fields[0],bytes));
        }
        assertEquals(List.of("Objects","Columns","Constraints","ForeignKeys","Indexes","RLS","Triggers","Functions"),new ArrayList<>(parts.keySet()));
        var combined=new java.io.ByteArrayOutputStream();for(byte[] bytes:parts.values())combined.write(bytes);
        assertEquals("eb39ddbfa59df338dcaa969e440c7d254b1cb27ace1211524375d786b2006ab1",HexFormat.of().formatHex(AiSql.hash(combined.toByteArray())));
        String definitions=new String(parts.get("Functions"),StandardCharsets.UTF_16LE);var matcher=Pattern.compile("(dbo:ToBitNV:|sec:fn_FilterByTenant:|sec:fn_FilterByTenantOrGlobal:)(.*?)::dbo;",Pattern.DOTALL).matcher(definitions);var modules=new LinkedHashMap<String,String>();
        while(matcher.find())modules.put(matcher.group(1).substring(0,matcher.group(1).length()-1).replace(':','.'),matcher.group(2));
        assertEquals(3,modules.size());return modules;
    }
}
