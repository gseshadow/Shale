package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.desktop.runtime.DesktopRuntimeSessionProvider;

final class DesktopRuntimeSessionLifecycleTest {
    @Test
    void runtimeArmConnectionAcquisitionAndClearShareOneMonitor() throws Exception {
        assertTrue(Modifier.isSynchronized(DesktopRuntimeSessionProvider.class
                .getMethod("setRuntime", com.shale.data.runtime.RuntimeSessionService.class).getModifiers()));
        assertTrue(Modifier.isSynchronized(DesktopRuntimeSessionProvider.class
                .getMethod("requireConnection").getModifiers()));
        assertTrue(Modifier.isSynchronized(DesktopRuntimeSessionProvider.class
                .getMethod("clear").getModifiers()));
    }

    @Test
    void staleLiveBusConnectionCannotReattachAfterLogoutOrUserSwitch() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/com/shale/desktop/ui/DesktopUiRuntimeBridge.java"));
        int completion = bridge.indexOf(".whenComplete((ok, ex)");
        int generationCheck = bridge.indexOf("generation != sessionGeneration.get()", completion);
        int publish = bridge.indexOf("liveBus = bus", completion);
        assertTrue(completion >= 0 && generationCheck > completion && publish > generationCheck,
                "a stale async connection must be rejected before it becomes the active LiveBus");
        int logout = bridge.indexOf("public void onLogout()");
        int invalidate = bridge.indexOf("sessionGeneration.incrementAndGet()", logout);
        int clear = bridge.indexOf("dbProvider.clear()", logout);
        assertTrue(invalidate > logout && clear > invalidate,
                "logout must invalidate pending LiveBus completions before clearing database authority");
    }
}
