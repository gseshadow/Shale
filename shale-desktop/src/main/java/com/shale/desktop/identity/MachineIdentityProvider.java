package com.shale.desktop.identity;

import com.shale.core.platform.AppPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MachineIdentityProvider {
	private static final Logger log = LoggerFactory.getLogger(MachineIdentityProvider.class);
	private final MachineIdentityStore store;
	private final Supplier<UUID> generator;

	public static MachineIdentityProvider platformDefault() {
		Path identityFile = AppPaths.machineDataDir("Shale").resolve("machine-id");
		return new MachineIdentityProvider(new FileMachineIdentityStore(identityFile), UUID::randomUUID);
	}

	MachineIdentityProvider(MachineIdentityStore store, Supplier<UUID> generator) {
		this.store = Objects.requireNonNull(store, "store");
		this.generator = Objects.requireNonNull(generator, "generator");
	}

	public MachineIdentityResult getOrCreate() {
		try {
			MachineIdentityStore.StoredMachineIdentity stored = store.getOrCreate(generator);
			switch (stored.state()) {
				case CREATED -> log.info("Stable machine identity created");
				case RECOVERED_CORRUPT -> log.warn("Malformed machine identity was preserved and replaced");
				case EXISTING -> { }
			}
			return MachineIdentityResult.available(stored.machineId());
		} catch (IOException | SecurityException exception) {
			log.error("Stable machine identity is unavailable because machine storage could not be accessed");
			return MachineIdentityResult.unavailable(MachineIdentityResult.Failure.STORAGE_ACCESS_FAILED);
		}
	}

	public static MachineIdentityResult resolvePlatformDefault() {
		try {
			return platformDefault().getOrCreate();
		} catch (IllegalStateException | UnsupportedOperationException | SecurityException exception) {
			log.error("Stable machine identity is unavailable on this platform");
			return MachineIdentityResult.unavailable(MachineIdentityResult.Failure.PLATFORM_STORAGE_UNAVAILABLE);
		}
	}
}
