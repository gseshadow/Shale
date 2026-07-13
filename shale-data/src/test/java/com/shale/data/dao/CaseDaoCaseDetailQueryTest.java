package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCaseDetailQueryTest {
    @Test
    void caseDetailSelectKeepsDateFieldsNullableAndStableForApiDetail() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "private com.shale.core.dto.CaseDetailDto selectCaseDetail", "private static com.shale.core.dto.CaseDetailDto mapCaseDetail");
        String mapper = method(source, "private static com.shale.core.dto.CaseDetailDto mapCaseDetail", "public com.shale.core.dto.CaseDetailDto updateCase");

        assertTrue(method.contains("c.CallerDate"));
        assertTrue(method.contains("c.AcceptedDate"));
        assertTrue(method.contains("c.ClosedDate"));
        assertTrue(method.contains("c.DeniedDate"));
        assertTrue(method.contains("c.DateOfInjury"));
        assertTrue(method.contains("c.StatuteOfLimitations"));
        assertTrue(method.contains("c.TortNoticeDeadline"));
        assertTrue(method.contains("c.MedicalRecordsRequested"));
        assertTrue(method.contains("c.UpdatedAt"));
        assertTrue(method.contains("schema.rowVersionSelectExpression(\"c\")"));

        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"CallerDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"AcceptedDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"ClosedDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"DeniedDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"DateOfInjury\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"StatuteOfLimitations\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"TortNoticeDeadline\"))"));
        assertTrue(mapper.contains("getNullableBoolean(rs, \"MedicalRecordsRequested\")"));
        assertTrue(mapper.contains("toLocalDateTime(rs.getTimestamp(\"UpdatedAt\"))"));
        assertTrue(mapper.contains("rs.getBytes(\"RowVer\")"));
    }

    @Test
    void caseDetailSelectDoesNotReferenceRemovedMedicalRecordsReceivedColumn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "private com.shale.core.dto.CaseDetailDto selectCaseDetail", "public com.shale.core.dto.CaseDetailDto updateCase");

        assertTrue(!method.contains("MedicalRecordsReceived"));
    }

    @Test
    void caseDetailSelectAliasesMissingRowVersionToPreserveMapperAndJsonShape() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String schema = method(source, "private record CaseSchema", "public record NewIntakeCreateRequest");
        String resolver = method(source, "private static CaseSchema resolveCaseSchema", "private static String resolveCaseUsersDeletedColumn");

        assertTrue(schema.contains("return \"NULL AS RowVer\""));
        assertTrue(schema.contains("return prefix + rowVersionColumn + \" AS RowVer\""));
        assertTrue(resolver.contains("existingColumn(con, CASES_TABLE, List.of(\"RowVer\", \"rowver\", \"RowVersion\", \"row_version\"))"));
    }

    private static String method(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        assertTrue(start >= 0, "Missing start: " + startNeedle);
        assertTrue(end > start, "Missing end: " + endNeedle);
        return source.substring(start, end);
    }
}
