package com.shale.core.dto;

import java.util.Arrays;
import java.util.List;

/** Authoritative baseline for the staged Case Overview administrator editor. */
public record CaseOverviewAdministrationDto(
        CaseOverviewDateConfigurationDto configuration,
        List<EffectiveCaseDateTypeDto> availableDateTypes,
        Integer intakeTakenByUserId,
        String intakeTakenByDisplayName,
        boolean intakeTakenByActive,
        byte[] caseRowVer) {
    public CaseOverviewAdministrationDto {
        availableDateTypes = availableDateTypes == null ? List.of() : List.copyOf(availableDateTypes);
        caseRowVer = copy(caseRowVer);
    }
    @Override public byte[] caseRowVer() { return copy(caseRowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
