package com.shale.core.update;

/** Keeps future scheduling code independent of workstation persistence details. */
@FunctionalInterface
public interface WorkstationUpdatePreferenceProvider {
	WorkstationUpdatePreference current();
}
