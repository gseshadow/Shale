package com.shale.core.service;

import com.shale.core.dto.FormConfigurationDto;
import java.util.Arrays;
import java.util.List;

public interface FormConfigurationServicePort {
    FormConfigurationDto load(int shaleClientId, int actorUserId, String formKey);
    FormConfigurationDto replace(ReplaceCommand command);

    record ReplaceCommand(int shaleClientId, int actorUserId, String formKey,
            List<SectionDraft> sections, byte[] expectedRowVer) {
        public ReplaceCommand { sections = sections == null ? List.of() : List.copyOf(sections); expectedRowVer = copy(expectedRowVer); }
        @Override public byte[] expectedRowVer() { return copy(expectedRowVer); }
    }
    record SectionDraft(String sectionKey, String title, int sortOrder, boolean enabled,
            boolean visible, List<FieldDraft> fields) {
        public SectionDraft { fields = fields == null ? List.of() : List.copyOf(fields); }
    }
    record FieldDraft(String fieldKey, String fieldKind, Integer caseDateTypeId, int sortOrder,
            boolean enabled, boolean visible, boolean required) {}

    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
