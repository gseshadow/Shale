package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;
import com.shale.ui.util.ExternalBrowserHelper;

final class CaseLinkUrlNormalizationUiTest {
	@Test
	void dialogValidationNormalizesInputForCreateOrUpdateCommand() {
		LinkTypeDto type = new LinkTypeDto(1, null, "Website", "#fff", true, false, null, null);
		CaseController.CaseLinkInput input = CaseController.validateCaseLinkDialogInput(type, "Name", "www.google.com", null, false, null);
		assertEquals("https://www.google.com", input.url());
	}

	@Test
	void invalidDialogInputIsRejectedBeforeClose() {
		LinkTypeDto type = new LinkTypeDto(1, null, "Website", "#fff", true, false, null, null);
		assertThrows(IllegalArgumentException.class, () -> CaseController.validateCaseLinkDialogInput(type, "Name", "ftp://example.com", null, false, null));
	}

	@Test
	void externalBrowserDefensivelyNormalizesWithoutNetworkAccessBeforeDelegating() {
		List<URI> opened = new ArrayList<>();
		ExternalBrowserHelper helper = new ExternalBrowserHelper(opened::add);
		helper.openHttpOrHttps("www.google.com");
		assertEquals(List.of(URI.create("https://www.google.com")), opened);
		assertThrows(IllegalArgumentException.class, () -> helper.openHttpOrHttps("javascript:alert(1)"));
		assertEquals(1, opened.size());
	}
}
