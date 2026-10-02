package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	void confirmedRevocationDeniesEveryNewJdbcAcquisitionAndUsesTerminalUiCallback() throws Exception {
		var provider = new DesktopRuntimeSessionProvider();
		provider.setRuntime(new com.shale.data.runtime.RuntimeSessionService(null));
		provider.clear();
		IllegalStateException denied = assertThrows(IllegalStateException.class, provider::requireConnection);
		assertTrue(denied.getMessage().contains("before login"), "revoked runtime authority must fail before opening JDBC work");
		String bridge = Files.readString(Path.of("src/main/java/com/shale/desktop/ui/DesktopUiRuntimeBridge.java"));
		String invalidation = method(bridge, "private synchronized void invalidateConfirmedSession");
		assertOrdered(invalidation, "heartbeat.stop()", "sessionGeneration.incrementAndGet()", "dbProvider.clear()", "runtimeSessionService.clear()", "sessionEndedHandler.run()");
	}

	private static String method(String source,String signature){int start=source.indexOf(signature);if(start<0)throw new AssertionError("Missing method: "+signature);int open=source.indexOf('{',start),depth=0;for(int i=open;i<source.length();i++){char value=source.charAt(i);if(value=='{')depth++;if(value=='}'&&--depth==0)return source.substring(start,i+1);}throw new AssertionError("Unclosed method: "+signature);}
	private static void assertOrdered(String source,String... fragments){int previous=-1;for(String fragment:fragments){int next=source.indexOf(fragment,previous+1);assertTrue(next>previous,"Expected lifecycle step in order: "+fragment);previous=next;}}
}
