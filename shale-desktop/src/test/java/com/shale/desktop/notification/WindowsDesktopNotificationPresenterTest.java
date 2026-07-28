package com.shale.desktop.notification;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.notification.NativeNotificationPresentation;
import com.shale.ui.notification.NoOpDesktopNotificationPresenter;
import com.shale.ui.notification.PresentationResult;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WindowsDesktopNotificationPresenterTest {
	private static NativeNotificationPresentation candidate() { return new NativeNotificationPresentation(7, "Shale", "Generic message", "TASK"); }
	@Test void unsupportedRuntimeNeverCreatesBridge() {
		assertInstanceOf(NoOpDesktopNotificationPresenter.class,
				DesktopNotificationPresenterFactory.create(WindowsNotificationRuntime.unsupported(), p -> { fail(); return null; }));
	}
	@Test void mapsSuccessAndRunsOffCallingThread() {
		FakeBridge bridge = new FakeBridge(); String caller = Thread.currentThread().getName();
		try (var presenter = new WindowsDesktopNotificationPresenter(bridge)) {
			assertEquals(PresentationResult.PRESENTED, presenter.present(candidate()));
			assertNotEquals(caller, bridge.thread.get());
			assertEquals("Shale", bridge.heading); assertEquals("Generic message", bridge.message);
		}
	}
	@Test void permanentInitializationFailureIsCached() {
		FakeBridge bridge = new FakeBridge(); bridge.initializeResult = WindowsNotificationBridge.UNSUPPORTED;
		try (var presenter = new WindowsDesktopNotificationPresenter(bridge)) {
			assertEquals(PresentationResult.UNSUPPORTED, presenter.present(candidate()));
			assertEquals(PresentationResult.UNSUPPORTED, presenter.present(candidate()));
			assertEquals(1, bridge.initializations);
		}
	}
	@Test void isolatedFailureAllowsLaterSuccessAndDoesNotLeakException() {
		FakeBridge bridge = new FakeBridge(); bridge.showResult = WindowsNotificationBridge.FAILED;
		try (var presenter = new WindowsDesktopNotificationPresenter(bridge)) {
			assertEquals(PresentationResult.FAILED, presenter.present(candidate()));
			bridge.showResult = WindowsNotificationBridge.PRESENTED;
			assertEquals(PresentationResult.PRESENTED, presenter.present(candidate()));
		}
	}
	@Test void closeClosesBridge() { FakeBridge bridge = new FakeBridge(); var presenter = new WindowsDesktopNotificationPresenter(bridge); presenter.close(); assertTrue(bridge.closed); }

	private static final class FakeBridge implements WindowsNotificationBridge {
		int initializeResult=PRESENTED, showResult=PRESENTED, initializations; boolean closed;
		String heading, message; AtomicReference<String> thread=new AtomicReference<>();
		public int initialize(String id) { initializations++; assertEquals(WindowsNotificationIdentity.APP_USER_MODEL_ID,id); return initializeResult; }
		public int show(String h,String m) { thread.set(Thread.currentThread().getName()); heading=h; message=m; return showResult; }
		public void close() { closed=true; }
	}
}
