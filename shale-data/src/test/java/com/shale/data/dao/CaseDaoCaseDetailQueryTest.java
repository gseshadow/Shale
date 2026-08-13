package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCaseDetailQueryTest {
    @Test
    void existingCaseDetailSelectExcludesAllNineMigratedCaseColumns() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "private com.shale.core.dto.CaseDetailDto selectCaseDetail", "private static com.shale.core.dto.CaseDetailDto mapCaseDetail");
        String mapper = method(source, "private static com.shale.core.dto.CaseDetailDto mapCaseDetail", "public CaseDetailDto updateCaseNonDate");

        assertTrue(method.contains("c.AcceptedDate"));
        assertTrue(method.contains("c.ClosedDate"));
        assertTrue(method.contains("c.DeniedDate"));
        for (String legacy : java.util.List.of("CallerDate", "CallerTime", "DateOfInjury", "DateOfMedicalNegligence",
                "DateMedicalNegligenceWasDiscovered", "StatuteOfLimitations", "TortNoticeDeadline",
                "DiscoveryDeadline", "DateFeeAgreementSigned", "DateNonEngagementLetterSent")) {
            assertTrue(!method.contains("c." + legacy), legacy + " must not be read by existing-case detail");
        }
        assertTrue(method.contains("c.MedicalRecordsRequested"));
        assertTrue(method.contains("c.UpdatedAt"));
        assertTrue(method.contains("schema.rowVersionSelectExpression(\"c\")"));

        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"AcceptedDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"ClosedDate\"))"));
        assertTrue(mapper.contains("toLocalDate(rs.getDate(\"DeniedDate\"))"));
        assertTrue(mapper.contains("getNullableBoolean(rs, \"MedicalRecordsRequested\")"));
        assertTrue(mapper.contains("toLocalDateTime(rs.getTimestamp(\"UpdatedAt\"))"));
        assertTrue(mapper.contains("rs.getBytes(\"RowVer\")"));
    }

    @Test
    void caseDetailSelectDoesNotReferenceRemovedMedicalRecordsReceivedColumn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "private com.shale.core.dto.CaseDetailDto selectCaseDetail", "public CaseDetailDto updateCaseNonDate");

        assertTrue(!method.contains("MedicalRecordsReceived"));
    }

    @Test
    void caseDetailHydratesIntakeUserInTenantSafeJoinWithoutExcludingHistoricalUsers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "private com.shale.core.dto.CaseDetailDto selectCaseDetail", "private static com.shale.core.dto.CaseDetailDto mapCaseDetail");
        String mapper = method(source, "private static com.shale.core.dto.CaseDetailDto mapCaseDetail", "public CaseDetailDto updateCaseNonDate");

        assertTrue(method.contains("intake_user.id = c.IntakeTakenByUserId"));
        assertTrue(method.contains("intake_user.ShaleClientId = c.ShaleClientId"));
        assertTrue(method.contains("CONCAT(intake_user.name_first, ' ', intake_user.name_last)"));
        assertTrue(!method.contains("intake_user.is_deleted"));
        assertTrue(mapper.contains("getNullableInt(rs, \"IntakeTakenByUserId\")"));
        assertTrue(mapper.contains("rs.getString(\"IntakeTakenByDisplayName\")"));
    }

    @Test
    void intakeCreationCapturesActorOnceAndValidatesItsTenant() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String insert = method(source, "private long insertCase", "private void validateIntakeUserForTenant");
        String validation = method(source, "private void validateIntakeUserForTenant", "private void validatePracticeAreaForTenant");

        assertTrue(insert.contains("validateIntakeUserForTenant(con, request.shaleClientId(), request.createdByUserId())"));
        assertTrue(insert.contains("IntakeTakenByUserId"));
        assertTrue(validation.contains("Id = ? AND ShaleClientId = ?"));
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
