package com.shale.desktop.update;

import com.shale.core.update.WorkstationUpdatePreference;
import java.io.IOException;

public interface WorkstationUpdatePreferenceStore {
	WorkstationUpdatePreference load() throws IOException;
	void save(boolean enabled) throws IOException;
}
