package com.shale.updater;

import com.shale.updater.platform.Platform;

public final class UpdateManifest {
	private String version;
	private String channel;
	private String zipUrl;
	private String installerUrl;
	private String windowsZipUrl;
	private String windowsInstallerUrl;
	private String windowsSha256;
	private String macZipUrl;
	private String macSha256;
	private String notes;
	private boolean mandatory;
	private String sha256;
	private String publishedAt;

	public String getVersion() {
		return version;
	}

	public String getChannel() {
		return channel;
	}

	public String getZipUrl() {
		return zipUrl;
	}

	public String getInstallerUrl() {
		return installerUrl;
	}

	public String getWindowsZipUrl() {
		return windowsZipUrl;
	}

	public String getWindowsInstallerUrl() {
		return windowsInstallerUrl;
	}

	public String getWindowsSha256() {
		return windowsSha256;
	}

	public String getMacZipUrl() {
		return macZipUrl;
	}

	public String getMacSha256() {
		return macSha256;
	}

	public String getNotes() {
		return notes;
	}

	public boolean isMandatory() {
		return mandatory;
	}

	public String getSha256() {
		return sha256;
	}

	public String getPublishedAt() {
		return publishedAt;
	}

	public String getZipUrl(Platform platform) {
		return switch (platform) {
			case WINDOWS -> firstNonBlank(windowsZipUrl, zipUrl);
			case MAC -> firstNonBlank(macZipUrl, zipUrl);
			default -> zipUrl;
		};
	}

	public String getInstallerUrl(Platform platform) {
		return switch (platform) {
			case WINDOWS -> firstNonBlank(windowsInstallerUrl, installerUrl);
			case MAC -> "";
			default -> installerUrl;
		};
	}

	public String getSha256(Platform platform) {
		return switch (platform) {
			case WINDOWS -> firstNonBlank(windowsSha256, sha256);
			case MAC -> firstNonBlank(macSha256, sha256);
			default -> sha256;
		};
	}

	private static String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred;
		}
		return fallback;
	}
}
