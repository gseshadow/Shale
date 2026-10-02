package com.shale.desktop.update;

import com.shale.core.platform.AppPaths;
import com.shale.core.update.WorkstationUpdatePreference;
import com.shale.core.update.WorkstationUpdatePreferenceProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutomaticUpdatePreferenceService implements WorkstationUpdatePreferenceProvider {
	private static final Logger log = LoggerFactory.getLogger(AutomaticUpdatePreferenceService.class);
	private final WorkstationUpdatePreferenceStore store;

	public static AutomaticUpdatePreferenceService platformDefault() {
		Path file = AppPaths.machineDataDir("Shale").resolve("automatic-update-preference.properties");
		return new AutomaticUpdatePreferenceService(new FileWorkstationUpdatePreferenceStore(file));
	}

	public static AutomaticUpdatePreferenceService resolvePlatformDefault() {
		try {
			return platformDefault();
		} catch (IllegalStateException | UnsupportedOperationException | SecurityException exception) {
			return new AutomaticUpdatePreferenceService(new WorkstationUpdatePreferenceStore() {
				@Override public WorkstationUpdatePreference load() throws IOException { throw new IOException("Machine storage unavailable"); }
				@Override public void save(boolean enabled) throws IOException { throw new IOException("Machine storage unavailable"); }
			});
		}
	}

	AutomaticUpdatePreferenceService(WorkstationUpdatePreferenceStore store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override public WorkstationUpdatePreference current() {
		try {
			WorkstationUpdatePreference preference = store.load();
			if (preference.status() == WorkstationUpdatePreference.Status.CORRUPT) {
				log.warn("Workstation automatic-update preference is corrupt; unattended execution is not permitted");
			}
			return preference;
		} catch (IOException | SecurityException exception) {
			log.error("Workstation automatic-update preference is unavailable; unattended execution is not permitted");
			return new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.UNAVAILABLE);
		}
	}

	public ChangeResult change(boolean enabled, boolean authenticatedAdministrator) {
		if (!authenticatedAdministrator) return ChangeResult.UNAUTHORIZED;
		try {
			store.save(enabled);
			log.info("Workstation automatic-update preference changed to {}", enabled ? "enabled" : "disabled");
			return ChangeResult.SAVED;
		} catch (IOException | SecurityException exception) {
			log.error("Workstation automatic-update preference could not be saved");
			return ChangeResult.UNAVAILABLE;
		}
	}

	public enum ChangeResult { SAVED, UNAUTHORIZED, UNAVAILABLE }
}
