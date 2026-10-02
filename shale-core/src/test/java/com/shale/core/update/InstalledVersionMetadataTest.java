package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstalledVersionMetadataTest {
	@TempDir Path directory;
	@Test void roundTripsCanonicalVersionAndProductionChannelAtomically() throws Exception {
		Path file=directory.resolve(InstalledVersionMetadata.FILE_NAME);
		var expected=InstalledVersionMetadata.production("1.0.129"); expected.writeAtomically(file);
		assertEquals(expected,InstalledVersionMetadata.read(file));
		assertEquals(1,Files.list(directory).count(),"Atomic publishing must not leave temporary metadata files");
	}
	@Test void rejectsMalformedVersionSchemaAndChannel() throws Exception {
		Path file=directory.resolve("bad.properties");
		for(String body:java.util.List.of("schemaVersion=1\nversion=v1\nchannel=PRODUCTION\n","schemaVersion=2\nversion=1.0.1\nchannel=PRODUCTION\n","schemaVersion=1\nversion=1.0.1\nchannel=stable\n")){
			Files.writeString(file,body); assertThrows(java.io.IOException.class,()->InstalledVersionMetadata.read(file));
		}
	}
}
