package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertFalse(source.contains("optionalColumnExpression(schema.addressHomeColumn(), \"c\", \"AddressHome\")"));
    }

    @Test void expertScalarIsOnlyACompatibilityWriteInProductionJava() throws Exception {
        String dao = contactDao();
        String mutation = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));
        assertFalse(dao.contains("IsExpert"));
        assertTrue(mutation.contains("SET IsExpert="));
        assertFalse(mutation.contains("SELECT IsExpert"));
        assertTrue(mutation.contains("d.SystemKey=N'expert'"));
    }
}
