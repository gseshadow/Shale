package com.shale.ui.component.dialog;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class CasePickerDialogAsyncTest {
    private static ExecutorService executor;

    @BeforeAll
    static void startFx() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @AfterAll
    static void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void showsResponsiveLoadingStateBeforeBlockedLookupCompletes() throws Exception {
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicBoolean lookupOnFxThread = new AtomicBoolean(true);
        AtomicReference<NewCalendarEventDialog.CaseOption> selected = new AtomicReference<>();

        CasePickerDialog.Handle handle = onFx(() -> CasePickerDialog.showAsync(null, () -> {
            lookupOnFxThread.set(Platform.isFxApplicationThread());
            loaderEntered.countDown();
            await(releaseLoader);
            return List.of(new NewCalendarEventDialog.CaseOption(42, "A long selectable case name", null, null, false));
        }, executor, selected::set));

        assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertTrue(handle.stage().isShowing(), "dialog is visible before lookup completion");
            assertEquals("Loading cases…", handle.state().getText());
            assertFalse(handle.cancel().isDisable(), "Cancel remains available while loading");
            assertTrue(handle.select().isDisable(), "Select requires a valid row");
            assertTrue(handle.search().getStyleClass().contains("shale-form-control"));
            assertTrue(handle.select().getStyleClass().contains("shale-control-primary"));
            assertTrue(handle.cancel().getStyleClass().contains("shale-control-secondary"));
            assertTrue(handle.retry().getStyleClass().contains("shale-control-secondary"));
            handle.stage().getScene().getRoot().applyCss();
            return null;
        });
        assertFalse(lookupOnFxThread.get());

        CountDownLatch fxResponsive = new CountDownLatch(1);
        Platform.runLater(fxResponsive::countDown);
        assertTrue(fxResponsive.await(2, TimeUnit.SECONDS), "FX thread remains responsive while lookup is blocked");
        releaseLoader.countDown();
        waitUntil(() -> onFx(() -> handle.list().getItems().size() == 1));
        onFx(() -> {
            handle.list().getSelectionModel().selectFirst();
            assertFalse(handle.select().isDisable());
            handle.select().fire();
            return null;
        });
        assertEquals(42, selected.get().caseId());
    }

    @Test
    void failureRetriesExactlyOnceAndClosedDialogRejectsLateCompletion() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch retryBlocked = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        CountDownLatch retryCompleted = new CountDownLatch(1);
        CasePickerDialog.Handle handle = onFx(() -> CasePickerDialog.showAsync(null, () -> {
            if (loads.incrementAndGet() == 1) throw new IllegalStateException("sensitive database detail");
            retryBlocked.countDown();
            await(releaseRetry);
            retryCompleted.countDown();
            return List.of(new NewCalendarEventDialog.CaseOption(7, "Late result", null, null, false));
        }, executor, ignored -> fail("closed dialog must not select")));

        waitUntil(() -> onFx(() -> handle.retry().isVisible()));
        onFx(() -> { assertEquals("Cases could not be loaded. Please try again.", handle.state().getText()); handle.retry().fire(); return null; });
        assertTrue(retryBlocked.await(5, TimeUnit.SECONDS));
        assertEquals(2, loads.get(), "Retry starts exactly one new load");
        onFx(() -> { handle.cancel().fire(); return null; });
        releaseRetry.countDown();
        assertTrue(retryCompleted.await(5, TimeUnit.SECONDS));
        onFx(() -> null);
        assertTrue(handle.disposed().get());
        assertTrue(onFx(() -> handle.list().getItems().isEmpty()), "late completion does not mutate detached controls");
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("controlled loader timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static void waitUntil(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (condition.call()) return;
            Thread.sleep(10);
        }
        fail("condition was not reached");
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
