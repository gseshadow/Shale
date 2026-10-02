package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UpdateAttemptStoreTest {
	private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

	@Test void lifecycleIsMonotonicAndDuplicateReportsAreIdempotent(@TempDir Path dir) throws Exception {
		UUID id=UUID.randomUUID(); UpdateAttemptStore store=store(dir,NOW);
		store.create(UpdateAttempt.start(id,"1.0.128","1.0.129",NOW));
		store.transition(id,UpdateAttemptState.UPDATER_LAUNCHED,null,null,null);
		store.transition(id,UpdateAttemptState.UPDATER_LAUNCHED,null,null,null);
		store.transition(id,UpdateAttemptState.INSTALL_APPLIED,null,null,null);
		UpdateAttempt completed=store.transition(id,UpdateAttemptState.COMPLETED,null,null,"1.0.129");
		assertEquals(UpdateAttemptState.COMPLETED,completed.state(),"a successful next start should become terminal");
		assertEquals(completed,store.transition(id,UpdateAttemptState.FAILED,UpdateFailureCode.INSTALL_APPLY_FAILED,null,null),
				"a terminal outcome must not be overwritten");
	}

	@Test void filesContainOnlyBoundedAllowlistedFields(@TempDir Path dir) throws Exception {
		UUID id=UUID.randomUUID(); UpdateAttemptStore store=store(dir,NOW);
		store.create(UpdateAttempt.start(id,"1.0.128","1.0.129",NOW));
		String serialized=Files.readString(dir.resolve(id+".properties"));
		for(String forbidden : new String[]{"password","jwt","jti","email","displayName","ip","location","machine","exception","stack"})
			assertFalse(serialized.toLowerCase().contains(forbidden.toLowerCase()),"local state must exclude "+forbidden);
		assertTrue(Files.size(dir.resolve(id+".properties"))<4096,"local state must remain bounded");
	}

	@Test void terminalFailureKeepsOnlyStableCode(@TempDir Path dir) throws Exception {
		UUID id=UUID.randomUUID(); UpdateAttemptStore store=store(dir,NOW);
		store.create(UpdateAttempt.start(id,"1.0.128",null,NOW));
		UpdateAttempt failed=store.transition(id,UpdateAttemptState.FAILED,UpdateFailureCode.MANIFEST_UNAVAILABLE,null,null);
		assertEquals(UpdateFailureCode.MANIFEST_UNAVAILABLE,failed.failureCode());
		assertThrows(Exception.class,()->store.transition(UUID.randomUUID(),UpdateAttemptState.FAILED,UpdateFailureCode.MANIFEST_UNAVAILABLE,null,null));
	}

	private static UpdateAttemptStore store(Path dir,Instant now){return new UpdateAttemptStore(dir,Clock.fixed(now,ZoneOffset.UTC));}
}
