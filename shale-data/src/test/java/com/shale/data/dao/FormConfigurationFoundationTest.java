package com.shale.data.dao;

import com.shale.core.service.FormConfigurationServicePort.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FormConfigurationFoundationTest {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();

    @Test void acceptsOrderedEmptyAndCaseDateConfiguration() throws Exception {
        assertEquals("NEW_INTAKE",validate(new ReplaceCommand(7,3,"NEW_INTAKE",List.of(),null)));
        assertEquals("NEW_INTAKE",validate(command(10,20)));
    }
    @Test void rejectsInvalidFormKindAndOrdering() {
        assertInvalid(new ReplaceCommand(7,3,"OTHER",List.of(),null));
        assertInvalid(new ReplaceCommand(7,3,"NEW_INTAKE",List.of(new SectionDraft("dates","Dates",-1,true,true,List.of())),null));
        assertInvalid(new ReplaceCommand(7,3,"NEW_INTAKE",List.of(new SectionDraft("dates","Dates",0,true,true,List.of(new FieldDraft("x","CUSTOM",null,0,true,true,false)))),null));
    }
    @Test void rejectsDuplicateCaseDateAcrossSections() { assertInvalid(new ReplaceCommand(7,3,"NEW_INTAKE",List.of(
            new SectionDraft("a","A",0,true,true,List.of(new FieldDraft("a.date","CASE_DATE",10,0,true,true,false))),
            new SectionDraft("b","B",1,true,true,List.of(new FieldDraft("b.date","CASE_DATE",10,0,true,true,false)))),null)); }

    @Test void migrationEnforcesTenantIsolationOrderingAndReferenceIntegrity() throws Exception {
        String sql=Files.readString(ROOT.resolve("docs/sql/2026-08-07_form_configuration_foundation_step1.sql"));
        assertAll(
                ()->assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId)")),
                ()->assertTrue(sql.contains("FOREIGN KEY(ShaleClientId,FormConfigurationId)")),
                ()->assertTrue(sql.contains("UX_FormConfigurationSections_Order")),
                ()->assertTrue(sql.contains("UX_FormConfiguredFields_SectionOrder")),
                ()->assertTrue(sql.contains("UX_FormConfiguredFields_CaseDate")),
                ()->assertTrue(sql.contains("FOREIGN KEY(CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id)")));
    }
    @Test void daoUsesConcurrencyGuardAndAtomicRollback() throws Exception {
        String source=Files.readString(ROOT.resolve("shale-data/src/main/java/com/shale/data/dao/FormConfigurationDao.java"));
        assertAll(
                ()->assertTrue(source.contains("WITH (UPDLOCK,HOLDLOCK)")),
                ()->assertTrue(source.contains("AND RowVer=?")),
                ()->assertTrue(source.contains("con.setAutoCommit(false)")),
                ()->assertTrue(source.contains("con.rollback()")),
                ()->assertTrue(source.contains("validateAdmin(con, command.shaleClientId(), command.actorUserId())")),
                ()->assertTrue(source.contains("ISNULL(is_admin,0)=1")),
                ()->assertTrue(source.indexOf("validateReferences") < source.indexOf("DELETE FROM dbo.FormConfiguredFields")),
                ()->assertTrue(source.contains("ShaleClientId=? AND (ShaleClientId=? OR ShaleClientId IS NULL)")));
    }

    private static ReplaceCommand command(int a,int b){return new ReplaceCommand(7,3,"NEW_INTAKE",List.of(new SectionDraft("dates","Dates",0,true,true,List.of(new FieldDraft("date.a","CASE_DATE",a,0,true,true,false),new FieldDraft("date.b","CASE_DATE",b,1,true,true,true)))),null);}
    private static String validate(ReplaceCommand command)throws Exception{Method m=FormConfigurationDao.class.getDeclaredMethod("validate",ReplaceCommand.class);m.setAccessible(true);return (String)m.invoke(null,command);}
    private static void assertInvalid(ReplaceCommand c){InvocationTargetException error=assertThrows(InvocationTargetException.class,()->validate(c));assertInstanceOf(IllegalArgumentException.class,error.getCause());}
}
