package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.PracticeAreaDto;

final class CaseDaoPracticeAreaMappingTest {

    @Test
    void effectivePracticeAreasPreferTenantRowsOverGlobalsBySystemKey() {
        List<PracticeAreaDto> globals = List.of(
                new PracticeAreaDto(4, "Medical Malpractice", "#111111", true, false, "medical_malpractice", null),
                new PracticeAreaDto(5, "Personal Injury", "#222222", true, false, "personal_injury", null),
                new PracticeAreaDto(6, "Sexual Assault", "#333333", true, false, "sexual_assault", null));
        List<PracticeAreaDto> tenant = List.of(
                new PracticeAreaDto(1, "Tenant Medical", "#aaaaaa", true, false, "medical_malpractice", 7),
                new PracticeAreaDto(2, "Tenant Personal", "#bbbbbb", true, false, "personal_injury", 7),
                new PracticeAreaDto(3, "Tenant Sexual", "#cccccc", true, false, "sexual_assault", 7));

        List<PracticeAreaDto> effective = CaseDao.resolveEffectivePracticeAreas(globals, tenant);

        assertEquals(List.of(1, 2, 3), effective.stream().map(PracticeAreaDto::id).sorted().toList());
    }

    @Test
    void effectivePracticeAreasKeepGlobalWhenTenantHasNoOverride() {
        List<PracticeAreaDto> globals = List.of(
                new PracticeAreaDto(4, "Medical Malpractice", "#111111", true, false, "medical_malpractice", null),
                new PracticeAreaDto(5, "Personal Injury", "#222222", true, false, "personal_injury", null));
        List<PracticeAreaDto> tenant = List.of(
                new PracticeAreaDto(7, "Tenant Medical", "#aaaaaa", true, false, "medical_malpractice", 8));

        List<PracticeAreaDto> effective = CaseDao.resolveEffectivePracticeAreas(globals, tenant);

        assertEquals(List.of(5, 7), effective.stream().map(PracticeAreaDto::id).sorted().toList());
    }

    @Test
    void effectivePracticeAreasCanFallbackToNormalizedNameForUnkeyedRows() {
        List<PracticeAreaDto> globals = List.of(
                new PracticeAreaDto(4, "Workers Comp", "#111111", true, false, null, null));
        List<PracticeAreaDto> tenant = List.of(
                new PracticeAreaDto(8, " workers comp ", "#aaaaaa", true, false, null, 8));

        List<PracticeAreaDto> effective = CaseDao.resolveEffectivePracticeAreas(globals, tenant);

        assertEquals(List.of(8), effective.stream().map(PracticeAreaDto::id).toList());
    }
}
