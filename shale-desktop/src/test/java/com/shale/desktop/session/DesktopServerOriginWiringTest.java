package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DesktopServerOriginWiringTest {
	@Test void compositionPassesOneResolvedOriginWithoutIndependentReads() throws Exception {
		String router=Files.readString(Path.of("src/main/java/com/shale/desktop/navigation/SceneRouter.java"));
		String bridge=Files.readString(Path.of("src/main/java/com/shale/desktop/ui/DesktopUiRuntimeBridge.java"));
		assertFalse(router.contains("System.getenv"));
		assertFalse(router.contains("System.getProperty(\"SHALE_SERVER_API_BASE_URL\""));
		assertFalse(bridge.contains("System.getenv"));
		assertFalse(bridge.contains("System.getProperty(\"SHALE_SERVER_API_BASE_URL\""));
		assertTrue(router.contains("serverApiOrigin"));
		assertTrue(bridge.contains("serverApiOrigin"));
	}
}
