package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the Phase 2E live-read boundary; migrations and compatibility writes are intentionally out of scope. */
class ContactPhase2ELegacyReadBoundaryContractTest {
    private static String contactDao() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
    }

    @Test void currentReadModelsSelectStructuredContactPointsWithoutScalarFallback() throws Exception {
        String source = contactDao();
        assertTrue(source.contains("currentPhoneExpression(\"c\", schema.tenantColumn())"));
        assertTrue(source.contains("currentEmailExpression(\"c\", schema.tenantColumn())"));
        assertTrue(source.contains("currentAddressExpression(\"c\", schema.tenantColumn())"));
        assertTrue(source.contains("ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id"));
        assertTrue(source.contains("ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id"));
        assertTrue(source.contains("ORDER BY a.IsPrimary DESC,a.SortOrder,a.Id"));
        assertFalse(source.contains("optionalColumnExpression(schema.emailColumn(), \"c\", \"Email\")"));
        assertFalse(source.contains("optionalColumnExpression(schema.phoneColumn(), \"c\", \"Phone\")"));
        assertFalse(source.contains("optionalColumnExpression(schema.addressColumn(), \"c\", \"Address\")"));
    }

    @Test void expertScalarHasNoRuntimeDependency() throws Exception {
        String dao = contactDao();
        String mutation = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));
        assertFalse(dao.contains("IsExpert"));
        assertFalse(mutation.contains("IsExpert"));
        assertFalse(mutation.contains("SELECT IsExpert"));
    }

    @Test void everyProductionLegacyReferenceHasAnExplicitNarrowClassification() throws Exception {
        List<String> legacy=List.of("PhoneCell","PhoneHome","PhoneWork","EmailPersonal","EmailWork","EmailOther","AddressHome","AddressWork","AddressOther","IsExpert");
        Path root=Path.of("src/main/java");
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String source=Files.readString(file);
                for(String column:legacy) {
                    Matcher matches=Pattern.compile("\\b"+column+"\\b").matcher(source);
                    while(matches.find()) {
                        String relative=root.relativize(file).toString().replace('\\','/');
                        String line=source.substring(source.lastIndexOf('\n',matches.start())+1,
                            source.indexOf('\n',matches.end())<0?source.length():source.indexOf('\n',matches.end())).trim();
                        assertTrue(isAllowlisted(relative,column,line),
                            ()->"Unclassified Contact legacy dependency: "+relative+" :: "+line);
                    }
                }
            }
        }
    }

    private static boolean isAllowlisted(String file,String column,String line) {
        if(file.equals("com/shale/data/dao/ContactMutationDao.java") || file.equals("com/shale/data/dao/ContactDao.java"))
            return false;
        if(file.equals("com/shale/data/dao/CaseDao.java"))
            return !column.equals("IsExpert") && (line.contains("ct.") || line.equals(column+",")); // documented immutable Case snapshots
        if(file.equals("com/shale/data/dao/UserDao.java"))
            return column.equals("PhoneCell") && line.contains("List.of"); // User schema, not Contact
        return false;
    }
}
