package com.shale.desktop.update;

import java.io.PrintStream;
import java.util.List;
import java.util.UUID;

import com.shale.core.update.WindowsInstallationRegistrationReader;

/** Narrow, read-only installed-machine entry point for validating the production registration reader. */
public final class WindowsInstallationRegistrationDiagnostic {
	private WindowsInstallationRegistrationDiagnostic() {}

	public static void main(String[] args) {
		if (args.length != 2 || !"--installation-id".equals(args[0])) {
			System.out.println("classification=INVALID_INVOCATION");
			System.exit(2);
		}
		final UUID installationId;
		try { installationId = UUID.fromString(args[1]); }
		catch (IllegalArgumentException invalid) {
			System.out.println("classification=INVALID_INVOCATION");
			System.exit(2);
			return;
		}
		int result = run(new WindowsInstallationRegistrationReader(
				WindowsInstallationRegistrationReader.windowsRegistrySource(),
				WindowsInstallationRegistrationReader::inspectPath), installationId, System.out);
		if (result != 0) System.exit(result);
	}

	static int run(WindowsInstallationRegistrationReader reader, UUID installationId, PrintStream output) {
		List<WindowsInstallationRegistrationReader.Result> matches = reader.read().stream()
				.filter(result -> result.registration() != null
						&& result.registration().installationId().equals(installationId))
				.toList();
		if (matches.size() != 1) {
			output.println("classification=" + (matches.isEmpty() ? "UNAVAILABLE" : "DUPLICATE_INSTALLATION_ID"));
			return 1;
		}
		var result = matches.getFirst();
		var registration = result.registration();
		output.println("classification=" + result.classification());
		output.println("schemaVersion=" + registration.schemaVersion());
		output.println("installationId=" + registration.installationId());
		output.println("ownerSid=" + registration.ownerSid());
		output.println("installRoot=" + registration.installRoot());
		output.println("supportRoot=" + registration.supportRoot());
		return result.classification() == WindowsInstallationRegistrationReader.Classification.VALID ? 0 : 1;
	}
}
