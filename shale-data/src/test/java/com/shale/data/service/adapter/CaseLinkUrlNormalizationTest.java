package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.shale.core.service.CaseServicePort.CreateCaseLinkCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseLinkCommand;
import com.shale.core.util.CaseLinkUrlNormalizer;

final class CaseLinkUrlNormalizationTest {
	@Test
	void normalizesSuccessfulInputs() {
		assertEquals("https://www.google.com", CaseLinkUrlNormalizer.normalize("www.google.com"));
		assertEquals("https://google.com", CaseLinkUrlNormalizer.normalize("google.com"));
		assertEquals("https://google.com/path", CaseLinkUrlNormalizer.normalize("google.com/path"));
		assertEquals("https://app.clio.com/nc/#/matters/1580621480", CaseLinkUrlNormalizer.normalize("app.clio.com/nc/#/matters/1580621480"));
		assertEquals("https://example.com/path", CaseLinkUrlNormalizer.normalize("//example.com/path"));
		assertEquals("http://example.com/path", CaseLinkUrlNormalizer.normalize("http://example.com/path"));
		assertEquals("https://www.google.com", CaseLinkUrlNormalizer.normalize("https://www.google.com"));
		assertEquals("HTTP://Example.com/Path", CaseLinkUrlNormalizer.normalize("HTTP://Example.com/Path"));
		assertEquals("https://google.com/path?q=One%20Two#Frag", CaseLinkUrlNormalizer.normalize("google.com/path?q=One%20Two#Frag"));
		assertEquals("https://google.com", CaseLinkUrlNormalizer.normalize("  google.com  "));
	}

	@Test
	void rejectsUnsafeOrUnsupportedInputs() {
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize(""));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("not a url"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("javascript:alert(1)"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("file:///tmp/x"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("ftp://example.com"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("mailto:test@example.com"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("data:text/plain,hi"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("https://user:pass@example.com"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("https:///missing-host"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("http://[::1"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("https://example.com/\nnext"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("example.com:"));
		assertThrows(IllegalArgumentException.class, () -> CaseLinkUrlNormalizer.normalize("example.com/" + "a".repeat(2048)));
	}

	@Test
	void serviceBoundaryNormalizesCreateAndUpdateBeforeGateway() {
		CaseServiceAdapterTest.FakeCaseGateway gateway = new CaseServiceAdapterTest.FakeCaseGateway(java.util.List.of());
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);
		adapter.createCaseLink(new CreateCaseLinkCommand(42, 7, 99, 3, "Name", "www.google.com", null, false, null, null));
		assertEquals("https://www.google.com", gateway.lastCaseLinkUrl);
		adapter.updateCaseLink(new UpdateCaseLinkCommand(42, 7, 99, 5, 6, 3, "Name", "google.com/path", null, null, null, null, new byte[] {1}, new byte[] {2}));
		assertEquals("https://google.com/path", gateway.lastCaseLinkUrl);
	}
}
