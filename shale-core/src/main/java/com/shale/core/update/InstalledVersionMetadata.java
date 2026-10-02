package com.shale.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;

/** Payload-lifecycle metadata; DisplayVersion and ordinary preferences are deliberately not authorities. */
public record InstalledVersionMetadata(int schemaVersion, SemanticVersion version, ReleaseChannel channel) {
	public static final String FILE_NAME = "shale-installed-version.properties";
	public InstalledVersionMetadata {
		if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported installed-version schema");
		Objects.requireNonNull(version, "version"); Objects.requireNonNull(channel, "channel");
	}
	public static InstalledVersionMetadata production(String version) {
		return new InstalledVersionMetadata(1, SemanticVersion.parse(version), ReleaseChannel.PRODUCTION);
	}
	public static InstalledVersionMetadata read(Path file) throws IOException {
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(file)) { p.load(in); }
		try { return new InstalledVersionMetadata(Integer.parseInt(required(p,"schemaVersion")),
				SemanticVersion.parse(required(p,"version")), ReleaseChannel.valueOf(required(p,"channel"))); }
		catch (RuntimeException ex) { throw new IOException("Installed-version metadata is malformed", ex); }
	}
	public void writeAtomically(Path file) throws IOException {
		Path absolute=file.toAbsolutePath().normalize(), parent=absolute.getParent();
		Files.createDirectories(parent); Path temporary=Files.createTempFile(parent,".installed-version-",".tmp");
		Properties p=new Properties(); p.setProperty("schemaVersion","1"); p.setProperty("version",version.toString()); p.setProperty("channel",channel.name());
		try { try(OutputStream out=Files.newOutputStream(temporary)){p.store(out,"Shale installed payload version");}
			Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
		} finally { Files.deleteIfExists(temporary); }
	}
	private static String required(Properties p,String key){String value=p.getProperty(key);if(value==null||value.isBlank())throw new IllegalArgumentException(key);return value.trim();}
}
