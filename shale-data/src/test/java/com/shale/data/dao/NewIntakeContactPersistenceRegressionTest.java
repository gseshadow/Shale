package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class NewIntakeContactPersistenceRegressionTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
    }

    @Test void clientAndCallerUseIndependentAuthoritativeStructuredContactPoints() throws Exception {
        String source = source();
        String create = source.substring(source.indexOf("public NewIntakeCreateResult createIntake"),
                source.indexOf("public static final class IntakeConfigurationException"));
        String caller = source.substring(source.indexOf("private int resolveCallerContactId"),
                source.indexOf("private int insertOrganization"));

        assertTrue(create.contains("request.clientPhone(),\n\t\t\t\t\trequest.clientEmail(), request.clientAddress()"));
        assertTrue(caller.contains("request.callerPhone(), request.callerEmail(),\n\t\t\t\trequest.callerAddress()"));
        assertTrue(source.contains("\"ContactPhoneNumbers\""));
        assertTrue(source.contains("\"ContactEmailAddresses\""));
        assertTrue(source.contains("\"ContactAddresses\""));
        assertTrue(source.contains("LegacyAddressText"), "free-form intake address uses the structured-address compatibility field");
        assertTrue(create.indexOf("insertIntakeContactPoints") < create.indexOf("insertCase(con"));
        assertTrue(create.indexOf("insertIntakeContactPoints") < create.indexOf("con.commit()"));
    }

    @Test void scalarContactInsertRetainsNamesBirthConditionAndDeceasedButNotRetiredPointColumns() throws Exception {
        String source = source();
        String insert = source.substring(source.indexOf("private int insertContact(Connection con,\n\t\t\tString name"),
                source.indexOf("private long insertCase"));

        for (String column : new String[] {"Name", "FirstName", "LastName", "DateOfBirth", "Condition", "IsDeceased", "IsClient"})
            assertTrue(Pattern.compile("\\b" + column + "\\b").matcher(insert).find(), column);
        for (String retired : new String[] {"PhoneCell", "EmailPersonal", "AddressHome"})
            assertFalse(Pattern.compile("\\b" + retired + "\\b").matcher(insert).find(), retired);
        assertTrue(source.contains("phiAuditService.auditUpdate(con, request.createdByUserId(), \"Contacts\", \"Condition\""));
    }

    @Test void blankPointsAreOptionalAndEverySupplementalFailureRemainsInTheIntakeTransaction() throws Exception {
        String source = source();
        assertTrue(source.contains("if (firstValue == null) return;"));
        assertTrue(source.contains("if (con != null) {\n\t\t\t\ttry {\n\t\t\t\t\tcon.rollback();"));
        assertTrue(source.contains("Failed to create structured Contact information."));
    }
}
