package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase535ContactIdentityTest {
    @Test
    void caseLinkContactOptionsPreserveExistingNameFirstDisplayMappingForLegacyNames() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String expression = source.substring(source.indexOf("private static String caseLinkShareContactDisplayNameExpression"),
                source.indexOf("private static List<CaseLinkContactOptionDto> mapCaseLinkContactOptions"));
        assertTrue(expression.contains("COALESCE(NULLIF(LTRIM(RTRIM("));
        assertTrue(expression.contains(".Name"));
        assertTrue(expression.indexOf(".Name") < expression.indexOf(".FirstName"));
        assertTrue(expression.contains(".WorkName"));
        assertFalse(expression.contains("Phone"));
    }
}
