package com.shale.core.model;

public record UpdateCalendarCaseDateTypeMappingCommand(
        long id, int calendarEventTypeId, int caseDateTypeId,
        boolean caseDateToCalendar, boolean calendarToCaseDate, byte[] expectedRowVer) {
    public UpdateCalendarCaseDateTypeMappingCommand { expectedRowVer = copy(expectedRowVer); }
    @Override public byte[] expectedRowVer() { return copy(expectedRowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
