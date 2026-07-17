package com.shale.core.dto;

import java.util.Objects;

public record ContactSharedCaseLinkDto(long caseId, String caseDisplayName, CaseLinkDto caseLink) {
    public ContactSharedCaseLinkDto {
        Objects.requireNonNull(caseLink, "caseLink");
    }
}
