package com.shale.updater;

public final class UpdateManifest {
	private String version;
	private String channel;
	private String zipUrl;
	private String installerUrl;
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
}