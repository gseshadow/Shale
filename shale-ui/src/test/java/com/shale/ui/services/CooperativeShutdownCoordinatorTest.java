package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.shale.core.update.CooperativeShutdownReadiness;

class CooperativeShutdownCoordinatorTest {
	@Test void onlyReadyUsesNormalLifecycleAndRechecksBeforeDispatch() {
		var state = new AtomicReference<>(CooperativeShutdownReadiness.ACTIVE_MUTATION_WORKFLOW);
		var shutdowns = new AtomicInteger();
		var queued = new AtomicReference<Runnable>();
		var coordinator = new CooperativeShutdownCoordinator(state::get, queued::set, shutdowns::incrementAndGet);
		assertEquals(CooperativeShutdownReadiness.ACTIVE_MUTATION_WORKFLOW, coordinator.requestIfReady());
		assertNull(queued.get(), "an open modal mutation must defer rather than attempting shutdown");
		state.set(CooperativeShutdownReadiness.READY);
		assertEquals(CooperativeShutdownReadiness.READY, coordinator.requestIfReady());
		state.set(CooperativeShutdownReadiness.SAVE_IN_FLIGHT);
		queued.get().run();
		assertEquals(0, shutdowns.get(), "a readiness change before shutdown dispatch must abort safely");
	}
}
