package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UpdateAttemptReconcilerTest {
	@Test void targetAndNewerVersionsConfirmCompletion(@TempDir Path dir) throws Exception {
		assertReconcilesCompleted(dir.resolve("target"),"1.0.129");
		assertReconcilesCompleted(dir.resolve("newer"),"1.0.130");
	}

	@Test void originalAndLowerVersionsDoNotClaimSuccess(@TempDir Path dir) throws Exception {
		assertRemainsApplied(dir.resolve("original"),"1.0.128");
		assertRemainsApplied(dir.resolve("lower"),"1.0.127");
	}

	@Test void unresolvedAttemptExpiresToUnknownWithoutGuessingFailure(@TempDir Path dir) throws Exception {
		Instant started=Instant.parse("2026-08-01T00:00:00Z"); UUID id=UUID.randomUUID();
		UpdateAttemptStore initial=store(dir,started); initial.create(UpdateAttempt.start(id,"1.0.128","1.0.129",started));
		initial.transition(id,UpdateAttemptState.UPDATER_LAUNCHED,null,null,null);
		UpdateAttemptStore later=store(dir,Instant.parse("2026-09-30T00:00:00Z"));
		new UpdateAttemptReconciler(later,Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"),ZoneOffset.UTC)).reconcile("1.0.128");
		assertEquals(UpdateAttemptState.OUTCOME_UNKNOWN,later.read(id).orElseThrow().state(),"expiry represents uncertainty, not failure");
	}

	private static void assertReconcilesCompleted(Path dir,String actual)throws Exception{UUID id=prepared(dir);UpdateAttemptStore s=store(dir,Instant.parse("2026-09-30T01:00:00Z"));new UpdateAttemptReconciler(s,Clock.fixed(Instant.parse("2026-09-30T01:00:00Z"),ZoneOffset.UTC)).reconcile(actual);assertEquals(UpdateAttemptState.COMPLETED,s.read(id).orElseThrow().state());assertEquals(actual,s.read(id).orElseThrow().actualVersion());new UpdateAttemptReconciler(s,Clock.fixed(Instant.parse("2026-09-30T01:00:00Z"),ZoneOffset.UTC)).reconcile(actual);assertEquals(UpdateAttemptState.COMPLETED,s.read(id).orElseThrow().state());}
	private static void assertRemainsApplied(Path dir,String actual)throws Exception{UUID id=prepared(dir);UpdateAttemptStore s=store(dir,Instant.parse("2026-09-30T01:00:00Z"));new UpdateAttemptReconciler(s,Clock.fixed(Instant.parse("2026-09-30T01:00:00Z"),ZoneOffset.UTC)).reconcile(actual);assertEquals(UpdateAttemptState.INSTALL_APPLIED,s.read(id).orElseThrow().state());}
	private static UUID prepared(Path dir)throws Exception{Instant n=Instant.parse("2026-09-30T00:00:00Z");UUID id=UUID.randomUUID();UpdateAttemptStore s=store(dir,n);s.create(UpdateAttempt.start(id,"1.0.128","1.0.129",n));s.transition(id,UpdateAttemptState.UPDATER_LAUNCHED,null,null,null);s.transition(id,UpdateAttemptState.INSTALL_APPLIED,null,null,null);return id;}
	private static UpdateAttemptStore store(Path dir,Instant now){return new UpdateAttemptStore(dir,Clock.fixed(now,ZoneOffset.UTC));}
}
