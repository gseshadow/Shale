package com.shale.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PerformanceLoggingTest {
    @Test
    void levelBoundariesMatchDesktopPolicy() {
        assertEquals(PerformanceLogging.Level.DEBUG, PerformanceLogging.levelForElapsed(999));
        assertEquals(PerformanceLogging.Level.INFO, PerformanceLogging.levelForElapsed(1_000));
        assertEquals(PerformanceLogging.Level.INFO, PerformanceLogging.levelForElapsed(1_999));
        assertEquals(PerformanceLogging.Level.WARN, PerformanceLogging.levelForElapsed(2_000));
    }

    @Test
    void failuresUseErrorLevelByPolicy() {
        assertEquals(PerformanceLogging.Level.ERROR, PerformanceLogging.Level.ERROR);
    }
}
