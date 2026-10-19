package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.state.AppState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatestSearchRunnerTest {
    @Test void invalidationCancelsOnlyTheCapturedStatementOffTheCallingThread() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var cancellations = new ArrayDeque<Runnable>();
        var runner = new LatestSearchRunner(worker::add, ui::add, cancellations::add);
        var cancelled = new ArrayList<String>();
        long first = runner.invalidate();
        runner.submit(first, () -> {
            runner.onCancel(first, () -> cancelled.add("old statement"));
            long latest = runner.invalidate();
            runner.invalidate(); // Repeated keystrokes must not enqueue another cancel for the same statement.
            assertTrue(cancelled.isEmpty(), "JDBC cancel must not execute on the typing/invalidation thread");
            assertEquals(1, cancellations.size());
            runner.onCancel(first, null);
            assertFalse(runner.isCurrent(latest));
            return "old";
        }, v -> fail("Stale result"), ex -> fail(ex));
        worker.remove().run();
        cancellations.remove().run();
        ui.forEach(Runnable::run);
        assertEquals(List.of("old statement"), cancelled);
    }

    @Test void invalidationBeforeStatementRegistrationStillCancelsAndQueuedReplacementRunsOnce() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var cancellations = new ArrayDeque<Runnable>();
        var runner = new LatestSearchRunner(worker::add, ui::add, cancellations::add);
        var calls = new ArrayList<String>();
        long old = runner.invalidate();
        runner.submit(old, () -> {
            long next = runner.invalidate();
            runner.onCancel(old, () -> calls.add("cancel"));
            runner.submit(next, () -> "latest", calls::add, ex -> fail(ex));
            return "old";
        }, v -> fail(), ex -> fail(ex));
        worker.remove().run();
        cancellations.remove().run();
        ui.forEach(Runnable::run);
        assertEquals(List.of("cancel", "latest"), calls);
    }

    @Test void requestsReplacedBeforeWorkerStartsNeverAcquireConnections() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var runner = new LatestSearchRunner(worker::add, ui::add);
        var shown = new ArrayList<Integer>();
        for (int i=0; i<100; i++) {
            long token = runner.invalidate();
            int value = i;
            runner.submit(token, () -> { assertEquals(99, value, "Obsolete queued work must not start"); return value; },
                    shown::add, ex -> fail(ex));
        }
        assertEquals(1, worker.size());
        worker.remove().run(); ui.forEach(Runnable::run);
        assertEquals(List.of(99), shown);
    }
    @Test void onlyLatestPendingQueryRunsAndQueuedCallbacksCannotRenderAfterInvalidation() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var runner = new LatestSearchRunner(worker::add, ui::add);
        var loads = new ArrayList<String>();
        var shown = new ArrayList<String>();
        long first = runner.invalidate();
        runner.submit(first, () -> {
            loads.add("first");
            for (String query : List.of("second", "third", "last")) {
                long token = runner.invalidate();
                runner.submit(token, () -> { loads.add(query); return query; }, shown::add, ex -> fail(ex));
            }
            return "first";
        }, shown::add, ex -> fail(ex));
        assertEquals(1, worker.size(), "There must be one worker, not a task per keystroke");
        worker.remove().run();
        ui.forEach(Runnable::run);
        assertEquals(List.of("first", "last"), loads, "Overlapping JDBC work must not accumulate");
        assertEquals(List.of("last"), shown, "A stale response must never render");
        runner.invalidate();
        ui.forEach(Runnable::run);
        assertEquals(List.of("last"), shown);
    }

    @Test void focusNavigationLogoutAndIdentityChangesInvalidateSuccessAndFailure() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var runner = new LatestSearchRunner(worker::add, ui::add);
        var state = new AppState();
        state.setUserId(11); state.setShaleClientId(7);
        state.addIdentityListener(runner::invalidate);
        for (Runnable invalidation : List.<Runnable>of(runner::invalidate,
                () -> state.setUserId(12), () -> state.setShaleClientId(8), state::clear)) {
            long token = runner.invalidate();
            runner.submit(token, () -> "Old session", v -> fail("Stale success"), ex -> fail("Stale failure"));
            worker.remove().run();
            invalidation.run();
            ui.remove().run();
            assertFalse(runner.isCurrent(token));
        }
        long token = runner.invalidate();
        runner.submit(token, () -> { throw new IllegalStateException("failure"); }, v -> fail(), ex -> fail("Stale error"));
        worker.remove().run();
        runner.invalidate();
        ui.remove().run();
    }

    @Test void identityRevisionRejectsReturningToTheSameUserAndTenant() {
        var state = new AppState();
        state.setUserId(11); state.setShaleClientId(7);
        long session = state.sessionRevision();
        state.setUserId(12); state.setUserId(11);
        assertTrue(state.sessionRevision() > session, "Equal IDs must not revive a prior session response");
        long current = state.sessionRevision();
        state.setUserId(11);
        assertEquals(current, state.sessionRevision(), "No-op setters should not cancel the current search");
    }
}
