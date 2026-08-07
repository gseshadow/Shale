package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDaoNonMigratedDesktopWriteContractTest {
    @Test void desktopNonDateBoundariesCannotWriteMigratedColumns() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String overview = source.substring(source.indexOf("updateCaseNonDate("), source.indexOf("updateCaseDetailsNonMigrated("));
        String details = source.substring(source.indexOf("updateCaseDetailsNonMigrated("), source.indexOf("public com.shale.core.dto.CaseDetailDto updateCaseDetails("));
        for (String column : new String[] {"CallerDate", "CallerTime", "DateOfMedicalNegligence",
                "DateMedicalNegligenceWasDiscovered", "DateOfInjury", "StatuteOfLimitations",
                "TortNoticeDeadline", "DiscoveryDeadline", "DateFeeAgreementSigned",
                "DateNonEngagementLetterSent"}) {
            assertFalse(overview.contains(column), column);
            assertFalse(details.contains(column), column);
        }
        assertTrue(overview.contains("RowVer = ?"));
        assertTrue(details.contains("RowVer=?"));
    }
}
