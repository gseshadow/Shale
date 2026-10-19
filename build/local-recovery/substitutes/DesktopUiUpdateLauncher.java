package com.shale.desktop.update;

import java.nio.file.Path;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.core.update.UpdateInvocationMode;

/** Explicit sandbox classpath substitute. This class is NEVER packaged into Shale.
 * Production update detection/install/enforcement is outside sandbox coverage. */
public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {
    public DesktopUiUpdateLauncher(){System.out.println("LOCAL SUBSTITUTE: production updater disabled");}
    @Override public UpdateCheckResult checkForUpdate(){return new UpdateCheckResult(false,false);}
    @Override public void launchUpdater(){throw new UnsupportedOperationException("Updates are disabled in the LOCAL sandbox");}
    @Override public void launchUpdater(UpdateInvocationMode mode){launchUpdater();}
    public static Path attemptDirectory(){return Path.of(System.getProperty("SHALE_UPDATE_ATTEMPT_DIR"));}
    public static Path executionLockPath(){return Path.of(System.getProperty("SHALE_UPDATE_EXECUTION_LOCK"));}
}
