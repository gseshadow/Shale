package com.shale.ui.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formats UTC-backed LocalDateTime values for local display.
 *
 * <p>Some persistence layers materialize UTC timestamps into {@link LocalDateTime} values,
 * which removes zone information. This helper explicitly interprets those values as UTC
 * and converts them into the user's local/system zone for display.
 */
public final class UtcDateTimeDisplayFormatter {

    private static final ZoneId SOURCE_ZONE = ZoneOffset.UTC;

    private UtcDateTimeDisplayFormatter() {
    }

    public static String formatUtcToLocal(LocalDateTime utcValue, DateTimeFormatter formatter) {
        if (utcValue == null) {
            return "—";
        }
        if (formatter == null) {
            throw new IllegalArgumentException("formatter is required");
        }

        ZoneId targetZone = ZoneId.systemDefault();
        LocalDateTime localized = utcValue.atZone(SOURCE_ZONE)
                .withZoneSameInstant(targetZone)
                .toLocalDateTime();
        return localized.format(formatter);
    }
}
