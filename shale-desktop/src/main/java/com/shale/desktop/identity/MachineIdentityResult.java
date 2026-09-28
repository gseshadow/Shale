package com.shale.desktop.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MachineIdentityResult(Optional<UUID> machineId, Optional<Failure> failure) {
	public enum Failure {
		PLATFORM_STORAGE_UNAVAILABLE,
		STORAGE_ACCESS_FAILED
	}

	public MachineIdentityResult {
		Objects.requireNonNull(machineId, "machineId");
		Objects.requireNonNull(failure, "failure");
		if (machineId.isPresent() == failure.isPresent()) {
			throw new IllegalArgumentException("Exactly one of machineId or failure must be present");
		}
	}

	public static MachineIdentityResult available(UUID machineId) {
		return new MachineIdentityResult(Optional.of(Objects.requireNonNull(machineId)), Optional.empty());
	}

	public static MachineIdentityResult unavailable(Failure failure) {
		return new MachineIdentityResult(Optional.empty(), Optional.of(Objects.requireNonNull(failure)));
	}

	public boolean isAvailable() {
		return machineId.isPresent();
	}
}
