package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Prevents the Phase 3B application from regaining a dbo.Contacts legacy-column dependency. */
class ContactPhase3BNoLegacyRuntimeContractTest {
    private static final List<String> RETIRING = List.of(
            "PhoneCell", "PhoneHome", "PhoneWork", "EmailPersonal", "EmailWork", "EmailOther",
            "AddressHome", "AddressWork", "AddressOther", "IsExpert");

    @Test void productionJavaHasNoRetiringContactsColumnDependency() throws Exception {
        Path root = Path.of("../");
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String column : RETIRING) {
                    var matcher = Pattern.compile("\\b" + column + "\\b").matcher(source);
                    while (matcher.find()) {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        String line = source.substring(source.lastIndexOf('\n', matcher.start()) + 1,
                                source.indexOf('\n', matcher.end()) < 0 ? source.length() : source.indexOf('\n', matcher.end())).trim();
                        assertTrue(unrelatedOrSnapshot(relative, column, line),
                                () -> "Retiring dbo.Contacts column in production: " + relative + " :: " + line);
                    }
                }
            }
        }
    }

    private static boolean unrelatedOrSnapshot(String file, String column, String line) {
        // User phone discovery is for the dbo.Users aggregate, not dbo.Contacts.
        if (file.endsWith("/UserDao.java"))
            return column.equals("PhoneCell") && line.contains("List.of(\"Phone\"");
        // These are the immutable dbo.CaseContacts snapshot insert columns. Current Contact reads are forbidden.
        if (file.endsWith("/CaseDao.java"))
            return line.equals(column + ",") && Set.of("AddressHome", "PhoneCell", "EmailPersonal").contains(column);
        return false;
    }

    @Test void mutationsTouchOnlyStructuredContactPointAndAssignmentTables() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));
        assertAll(
                () -> assertTrue(source.contains("ContactPhoneNumbers")),
                () -> assertTrue(source.contains("ContactEmailAddresses")),
                () -> assertTrue(source.contains("ContactAddresses")),
                () -> assertTrue(source.contains("ContactContactTypes")),
                () -> assertFalse(source.contains("projectLegacy")),
                () -> RETIRING.forEach(column -> assertFalse(Pattern.compile("\\b" + column + "\\b").matcher(source).find())));
    }
}
