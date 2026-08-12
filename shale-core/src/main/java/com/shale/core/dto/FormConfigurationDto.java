package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Complete, ordered, tenant-owned configuration for one stable form key. */
public record FormConfigurationDto(long id, int shaleClientId, String formKey,
        List<Section> sections, LocalDateTime createdAt, LocalDateTime updatedAt, byte[] rowVer) {
    public FormConfigurationDto {
        sections = sections == null ? List.of() : List.copyOf(sections);
        rowVer = copy(rowVer);
    }
    @Override public byte[] rowVer() { return copy(rowVer); }

    public record Section(long id, String sectionKey, String title, int sortOrder,
            boolean enabled, boolean visible, List<Field> fields) {
        public Section { fields = fields == null ? List.of() : List.copyOf(fields); }
    }

    public record Field(long id, String fieldKey, String fieldKind, Integer caseDateTypeId,
            int sortOrder, boolean enabled, boolean visible, boolean required) {}

    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
