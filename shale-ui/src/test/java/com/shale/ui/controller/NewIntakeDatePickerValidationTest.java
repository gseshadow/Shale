package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.DatePicker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NewIntakeDatePickerValidationTest {
    @BeforeAll static void startToolkit() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
    }

    @Test void malformedTypedDateIsCapturedAsValidationInsteadOfEscapingCommit() throws Exception {
        onFxThread(() -> {
            DatePicker picker = new DatePicker(LocalDate.of(2026, 9, 1));
            NewIntakeController.configureDatePicker(picker);
            assertDoesNotThrow(() -> picker.getConverter().fromString("12345"));
            assertTrue(NewIntakeController.hasInvalidDateText(picker));
            assertEquals(LocalDate.of(2026, 9, 1), picker.getConverter().fromString("12345"));
        });
    }

    @Test void validTypedBlankOptionalAndPickerSelectedDatesRemainSupported() throws Exception {
        onFxThread(() -> {
            DatePicker picker = new DatePicker();
            NewIntakeController.configureDatePicker(picker);
            LocalDate typed = picker.getConverter().fromString("9/1/2026");
            assertEquals(LocalDate.of(2026, 9, 1), typed);
            assertFalse(NewIntakeController.hasInvalidDateText(picker));
            assertNull(picker.getConverter().fromString("   "));
            LocalDate selected = LocalDate.of(2026, 10, 2);
            picker.setValue(selected);
            assertEquals(selected, picker.getValue());
            assertEquals(selected, picker.getConverter().fromString(picker.getConverter().toString(selected)));
        });
    }

    private static void onFxThread(Runnable work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { work.run(); } catch (Throwable t) { failure.set(t); } finally { done.countDown(); } });
        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX work timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
