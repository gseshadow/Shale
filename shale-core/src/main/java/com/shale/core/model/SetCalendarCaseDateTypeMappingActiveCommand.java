package com.shale.core.model;

public record SetCalendarCaseDateTypeMappingActiveCommand(long id, boolean active, byte[] expectedRowVer) {
    public SetCalendarCaseDateTypeMappingActiveCommand { expectedRowVer = copy(expectedRowVer); }
    @Override public byte[] expectedRowVer() { return copy(expectedRowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
