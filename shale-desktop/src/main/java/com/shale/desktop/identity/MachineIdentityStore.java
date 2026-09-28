package com.shale.desktop.identity;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

public interface MachineIdentityStore {
	StoredMachineIdentity getOrCreate(Supplier<UUID> generator) throws IOException;

	record StoredMachineIdentity(UUID machineId, State state) {
		public enum State { EXISTING, CREATED, RECOVERED_CORRUPT }
	}
}
