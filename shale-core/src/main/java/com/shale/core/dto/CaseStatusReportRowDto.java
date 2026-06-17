package com.shale.core.dto;

public record CaseStatusReportRowDto(
        int statusId,
        String caseStatus,
        String systemKey,
        String color,
        long caseCount
) {
}
