package com.shale.ui.controller.support;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.FormConfigurationDto;
import com.shale.core.service.FormConfigurationServicePort.FieldDraft;
import com.shale.core.service.FormConfigurationServicePort.SectionDraft;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure model operations for the configurable New Intake Dates section. */
public final class NewIntakeDatesConfiguration {
    public static final String FORM_KEY = "NEW_INTAKE";
    public static final String SECTION_KEY = "dates";
    public static final String FIELD_KIND = "CASE_DATE";

    private NewIntakeDatesConfiguration() {}

    public static String fieldKey(int caseDateTypeId) {
        if (caseDateTypeId <= 0) throw new IllegalArgumentException("CaseDateTypeId must be positive.");
        return "case_date:" + caseDateTypeId;
    }

    public static List<ConfiguredDate> renderable(FormConfigurationDto configuration,
            List<EffectiveCaseDateTypeDto> effectiveTypes) {
        Map<Integer, EffectiveCaseDateTypeDto> byId = effectiveById(effectiveTypes, configuration);
        if (configuration == null || configuration.id() == 0)
            return byId.values().stream().map(type -> new ConfiguredDate(fieldKey(type.id()), type, false)).toList();
        return configuration.sections().stream()
                .filter(s -> SECTION_KEY.equals(s.sectionKey()) && s.enabled() && s.visible())
                .flatMap(s -> s.fields().stream())
                .filter(f -> FIELD_KIND.equals(f.fieldKind()) && f.enabled() && f.visible()
                        && f.caseDateTypeId() != null && byId.containsKey(f.caseDateTypeId()))
                .sorted(Comparator.comparingInt(FormConfigurationDto.Field::sortOrder)
                        .thenComparingLong(FormConfigurationDto.Field::id))
                .map(f -> new ConfiguredDate(f.fieldKey(), byId.get(f.caseDateTypeId()), f.required()))
                .toList();
    }

    public static List<Selection> selections(FormConfigurationDto configuration,
            List<EffectiveCaseDateTypeDto> effectiveTypes) {
        Map<Integer, EffectiveCaseDateTypeDto> byId = effectiveById(effectiveTypes, configuration);
        if (configuration == null || configuration.id() == 0)
            return byId.values().stream().map(type -> new Selection(type, false)).toList();
        return configuration.sections().stream().filter(s -> SECTION_KEY.equals(s.sectionKey()))
                .flatMap(s -> s.fields().stream())
                .filter(f -> FIELD_KIND.equals(f.fieldKind()) && f.caseDateTypeId() != null
                        && byId.containsKey(f.caseDateTypeId()))
                .sorted(Comparator.comparingInt(FormConfigurationDto.Field::sortOrder)
                        .thenComparingLong(FormConfigurationDto.Field::id))
                .map(f -> new Selection(byId.get(f.caseDateTypeId()), f.required()))
                .toList();
    }

    public static SectionDraft draft(List<Selection> selections) {
        List<FieldDraft> fields = new ArrayList<>();
        for (int i = 0; i < selections.size(); i++) {
            Selection selected = selections.get(i);
            fields.add(new FieldDraft(fieldKey(selected.type().id()), FIELD_KIND,
                    selected.type().id(), i, true, true, selected.required()));
        }
        return new SectionDraft(SECTION_KEY, "Dates", 0, true, true, fields);
    }

    /** Returns a new draft selection with only its required state changed. */
    public static Selection withRequired(Selection selection, boolean required) {
        if (selection == null) throw new IllegalArgumentException("Selection is required.");
        return new Selection(selection.type(), required);
    }

    private static Map<Integer, EffectiveCaseDateTypeDto> effectiveById(List<EffectiveCaseDateTypeDto> types,
            FormConfigurationDto configuration) {
        Map<Integer, EffectiveCaseDateTypeDto> result = new LinkedHashMap<>();
        Integer tenantId = configuration == null ? null : configuration.shaleClientId();
        if (types != null) for (EffectiveCaseDateTypeDto type : types) {
            if (type == null) continue;
            boolean tenantVisible = tenantId == null || type.shaleClientId() == null
                    || type.shaleClientId().equals(tenantId);
            if (tenantVisible && type.active() && !type.deleted()) result.putIfAbsent(type.id(), type);
        }
        return result;
    }

    public record ConfiguredDate(String fieldKey, EffectiveCaseDateTypeDto type, boolean required) {}
    public record Selection(EffectiveCaseDateTypeDto type, boolean required) {}
}
