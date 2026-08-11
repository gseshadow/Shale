package com.shale.core.model;

public record DeleteCalendarCaseDateTypeMappingCommand(long id, byte[] expectedRowVer) {
    public DeleteCalendarCaseDateTypeMappingCommand { expectedRowVer = copy(expectedRowVer); }
    @Override public byte[] expectedRowVer() { return copy(expectedRowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
