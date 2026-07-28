package com.shale.desktop.notification;

import java.nio.file.Path;

final class JniWindowsNotificationBridge implements WindowsNotificationBridge {
	JniWindowsNotificationBridge(Path library) { System.load(library.toAbsolutePath().toString()); }
	@Override public native int initialize(String appUserModelId);
	@Override public native int show(String heading, String message);
	@Override public native void close();
}
