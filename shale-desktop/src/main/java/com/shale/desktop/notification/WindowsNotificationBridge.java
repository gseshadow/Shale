package com.shale.desktop.notification;

interface WindowsNotificationBridge extends AutoCloseable {
	int PRESENTED = 0, UNSUPPORTED = 1, FAILED = 2;
	int initialize(String appUserModelId);
	int show(String heading, String message);
	@Override void close();
}
