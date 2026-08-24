package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ContactFoundationMigrationContractTest {
    private static final Pattern GO_BATCH = Pattern.compile("(?im)^\\s*GO\\s*(?:--.*)?$");
    private static Path repo(String path) { Path p=Path.of(path); return Files.exists(p)?p:Path.of("..").resolve(path); }
    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test void migrationDefinesTheCompleteAdditiveContract() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        for(String table:new String[]{"ContactTypes","Specialties","CredentialDefinitions","ContactContactTypes","ContactSpecialties","ContactCredentials"}) {
            assertTrue(s.contains("CREATE TABLE dbo."+table),table);
            assertTrue(s.contains("(N'"+table+"'"),"full expected-column metadata: "+table);
        }
        for(String column:new String[]{"Prefix","MiddleName","PreferredName","Suffix"}) assertTrue(s.contains("Contacts ADD "+column),column);
        assertTrue(s.contains("Abbreviation nvarchar(50) NOT NULL"));
        assertTrue(s.contains("N'CredentialDefinitions',N'Abbreviation',N'nvarchar',100,0,0,0"));
        assertTrue(s.contains("later additive phases may extend these tables"));
        assertFalse(s.contains("Unexpected column in a Contacts Phase 1A table"));
        assertTrue(s.contains("Critical default constraint is missing or incompatible"));
        assertTrue(s.contains("incompatible keys, uniqueness, includes, or filter"));
        assertTrue(s.contains("incompatible parent/column mapping"));
        assertFalse(s.contains("INSERT dbo.CredentialDefinitions"));
        assertFalse(s.contains("DROP COLUMN"));
    }

    @Test void lifecycleTenantAndActorContractsAreEnforced() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        assertTrue(s.contains("IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL"));
        assertTrue(s.contains("IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL"));
        for(String mapping:new String[]{
                "(N'FK_ContactContactTypes_Contact_Tenant',N'ContactContactTypes',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id')",
                "(N'FK_ContactSpecialties_Contact_Tenant',N'ContactSpecialties',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id')",
                "(N'FK_ContactCredentials_Contact_Tenant',N'ContactCredentials',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id')"})
            assertTrue(s.contains(mapping),mapping);
        assertTrue(s.contains("DECLARE fk_create CURSOR LOCAL FAST_FORWARD FOR SELECT ConstraintName,ChildTable,ChildColumns,ParentTable,ParentColumns FROM @Fks"));
        assertTrue(s.contains("FOREIGN KEY('+@cc+N') REFERENCES dbo.'+QUOTENAME(@pt)+N'('+@pc+N')"));
        assertTrue(s.contains("x.ChildColumns<>e.ChildColumns OR x.ParentColumns<>e.ParentColumns"));
        assertTrue(s.contains("Required foreign key name exists with incompatible parent/column mapping"));
        assertTrue(s.contains("REFERENCES dbo.Users(id)"));
        for(String actor:new String[]{"CreatedByUserId","UpdatedByUserId","DeletedByUserId"}) assertTrue(s.contains("(N'"+actor+"')"),actor);
        assertTrue(s.contains("DisplayOrder>=0"));
        assertTrue(s.contains("WHERE IsDeleted=0"));
    }

    @Test void migrationGuardsAllTenantContextContactsAndExpertCompatibility() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(s.contains("USER_NAME() IN (N'shale_app',N'shale_runtime')"));
        assertTrue(s.contains("IS_SRVROLEMEMBER(N'sysadmin')"));
        assertTrue(s.contains("IS_MEMBER(N'db_owner')"));
        assertTrue(s.contains("@OperatorVerifiedAllTenantVisibility<>1"));
        assertTrue(s.contains("dbo.Contacts.Id must be the NOT NULL int IDENTITY primary key"));
        assertTrue(s.contains("dbo.Contacts.ShaleClientId must be int NOT NULL"));
        assertTrue(s.contains("LEFT JOIN dbo.ShaleClients sc"));
        assertTrue(s.contains("Duplicate Contacts (ShaleClientId,Id)"));
        assertTrue(s.contains("Duplicate global expert definitions"));
        assertTrue(s.contains("pre-existing global expert definition is incompatible"));
        assertTrue(s.contains("WHERE c.IsExpert=1 AND NOT EXISTS"));
        assertFalse(s.contains("UPDATE dbo.Contacts"));
    }

    @Test void operatorGuardAndEveryMigrationMutationAreOneExecutableBatch() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        int tryStart=s.indexOf("BEGIN TRY");
        int acknowledgement=s.indexOf("DECLARE @OperatorVerifiedAllTenantVisibility bit=0");
        int sessionGuard=s.indexOf("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL");
        int principalGuard=s.indexOf("USER_NAME() IN (N'shale_app',N'shale_runtime')");
        int transaction=s.indexOf("BEGIN TRANSACTION;");
        int firstDdl=s.indexOf("ALTER TABLE dbo.Contacts ADD Prefix");
        int expertBackfill=s.indexOf("INSERT dbo.ContactContactTypes(ShaleClientId,ContactId,ContactTypeId,IsDeleted,CreatedAt)");
        int rls=s.indexOf("ALTER SECURITY POLICY ");
        int commit=s.indexOf("COMMIT TRANSACTION;");
        int catchBlock=s.indexOf("END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;");
        assertTrue(tryStart>=0 && tryStart<acknowledgement && acknowledgement<sessionGuard
                && sessionGuard<principalGuard && principalGuard<transaction && transaction<firstDdl
                && firstDdl<expertBackfill && expertBackfill<rls && rls<commit && commit<catchBlock);
        Matcher batches=GO_BATCH.matcher(s);
        assertTrue(batches.find(),"the optional trailing batch separator remains");
        assertTrue(batches.start()>catchBlock,"GO must follow the complete guarded TRY/CATCH batch");
        assertFalse(batches.find(),"there must be no later mutation batch");
        assertTrue(s.contains("SET XACT_ABORT ON;"));
        assertTrue(s.contains("@OperatorVerifiedAllTenantVisibility bit=0"));
    }

    @Test void verificationGuardGovernsEveryQueryInOneBatch() throws Exception {
        String v=read("docs/sql/verification/2026-08-24_contacts_foundation_phase1a_verification.sql");
        assertTrue(v.indexOf("BEGIN TRY")<v.indexOf("DECLARE @OperatorVerifiedAllTenantVisibility bit=0"));
        assertTrue(v.indexOf("@OperatorVerifiedAllTenantVisibility<>1")<v.indexOf("/* Exact column contract"));
        assertTrue(v.trim().endsWith("END TRY BEGIN CATCH THROW; END CATCH;"));
        assertFalse(GO_BATCH.matcher(v).find(),"a failed guard must not expose a later verification batch");
    }

    @Test void exactEstablishedRlsContractIsValidatedWithoutContactsExpansion() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        String v=read("docs/sql/verification/2026-08-24_contacts_foundation_phase1a_verification.sql");
        for(String sql:new String[]{s,v}) {
            for(String table:new String[]{"ContactTypes","Specialties","CredentialDefinitions"}) assertTrue(sql.contains("(N'"+table+"',N'sec.fn_filterbytenantorglobalshaleclientid')"));
            for(String table:new String[]{"ContactContactTypes","ContactSpecialties","ContactCredentials"}) assertTrue(sql.contains("(N'"+table+"',N'sec.fn_filterbytenantshaleclientid')"));
            assertTrue(sql.contains("sp.object_id=@PolicyId"));
            assertTrue(sql.contains("sp.target_object_id=OBJECT_ID(N'dbo.'+e."));
            assertTrue(sql.contains("sp.predicate_type_desc=N'FILTER'"));
            assertTrue(sql.contains("sp.predicate_definition,N'[',N''"));
            assertTrue(sql.contains("NCHAR(9)"));
            assertTrue(sql.contains("NCHAR(10)"));
            assertTrue(sql.contains("NCHAR(13)"));
            assertFalse(sql.contains("predicate_object_id"));
            assertFalse(sql.contains("predicate_definition LIKE"));
        }
        assertTrue(s.contains("exactly one expected FILTER function"));
        assertTrue(s.contains("Unexpected policy or non-FILTER predicate"));
        assertTrue(s.contains("x.n<>1"));
        assertTrue(v.contains(")<>1;"));
        assertFalse(s.contains("PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Contacts"));
        assertTrue(v.contains("target_object_id=OBJECT_ID(N'dbo.Contacts')"));
        assertFalse(s.contains("CREATE SECURITY POLICY"));
    }

    @Test void verificationAndArchitectureCoverLiveBaselineAndDeferredRules() throws Exception {
        String v=read("docs/sql/verification/2026-08-24_contacts_foundation_phase1a_verification.sql");
        String a=read("architecture/contact-management.md");
        String d=read("architecture/database-schema.md");
        for(String phrase:new String[]{"column contract mismatches (expect 0)","critical defaults missing/incompatible (expect 0)","actor FK mapping mismatches (expect 0)","global Expert definitions (expect exactly 1 after migration)","legacy IsExpert=1 Contacts (live baseline observed 0)","missing active Expert assignments (expect 0)","active Expert assignments (live baseline currently expected 0)","duplicate active Expert assignments (expect 0)","cross-tenant Contact assignments (expect 0)","cross-tenant definition assignments (expect 0)","unexpected Phase 1A RLS predicates (expect 0)","dbo.Contacts TenantFilter predicates (existing condition; expect 0 in Phase 1A)"}) assertTrue(v.contains(phrase),phrase);
        assertTrue(v.contains("Abbreviation"));
        assertTrue(v.contains("additional non-Phase-1A columns (allowed; informational)"));
        assertTrue(v.contains("@OperatorVerifiedAllTenantVisibility<>1"));
        assertTrue(v.contains("STRING_AGG"));
        assertTrue(a.contains("2,314 tenant-7 and 10 tenant-8 Contacts"));
        assertTrue(a.contains("dual-write"));
        assertTrue(a.contains("PartyRoles.SystemKey=expert"));
        assertTrue(a.contains("no TenantFilter predicate"));
        assertTrue(a.contains("audit entity/action vocabulary") || a.contains("entity/action vocabulary"));
        assertTrue(d.contains("full `Name`"));
        assertTrue(d.contains("current runtime reads, writes, and display behavior remain unchanged"));
        assertTrue(a.contains("has **not** been\nexecuted"));
    }

    @Test void rerunValidationToleratesUnrelatedLaterAdditiveObjects() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        assertFalse(s.contains("NOT EXISTS(SELECT 1 FROM @ExpectedColumns e WHERE e.TableName=t.name AND e.ColumnName=c.name)"));
        assertTrue(s.contains("Unrelated checks added by later phases are intentionally tolerated"));
        assertTrue(s.contains("WHERE i.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND i.name=e.IndexName"));
        assertTrue(s.contains("f.name=e.ConstraintName"));
        assertTrue(s.contains("c.name=e.ConstraintName"));
    }

    @Test void phaseOneADoesNotMutateCaseRoleOrAuditArchitecture() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        for(String table:new String[]{"PartyRoles","CaseParties","CaseContacts","EntityActionAuditLog"}) {
            assertFalse(s.contains("UPDATE dbo."+table),table);
            assertFalse(s.contains("INSERT dbo."+table),table);
            assertFalse(s.contains("ALTER TABLE dbo."+table),table);
        }
    }
}
