package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;

class DesktopApplicationInstanceVerifierTest {
	private static final ServerPrincipal OWNER=new ServerPrincipal(9,7,"owner@test");
	private static final SemanticVersion VERSION=new SemanticVersion(1,0,130);

	@Test void acceptsActiveDesktopOwnedByAuthenticatedTenantAndUser() {
		var verifier=new DesktopApplicationInstanceVerifier((principal,id) -> desktop(id,null));
		assertTrue(verifier.isAttachable(OWNER,44));
	}

	@Test void rejectsWrongTenantWrongUserAndMissingInstances() {
		for(ServerPrincipal supplied:new ServerPrincipal[]{new ServerPrincipal(9,8,"owner@test"),new ServerPrincipal(10,7,"other@test")}) {
			var verifier=new DesktopApplicationInstanceVerifier((principal,id) -> {
				if(!principal.equals(OWNER))throw new SecurityException("unavailable");
				return desktop(id,null);
			});
			assertFalse(verifier.isAttachable(supplied,44));
		}
		assertFalse(new DesktopApplicationInstanceVerifier((principal,id) -> {throw new SecurityException("unavailable");}).isAttachable(OWNER,404));
	}

	@Test void rejectsNonDesktopAndEndedInstances() {
		assertFalse(new DesktopApplicationInstanceVerifier((principal,id) -> instance(id,ClientType.WEB,null)).isAttachable(OWNER,44));
		assertFalse(new DesktopApplicationInstanceVerifier((principal,id) -> desktop(id,Instant.EPOCH.plusSeconds(1))).isAttachable(OWNER,44));
	}

	private static ApplicationInstanceView desktop(long id,Instant endedAt) {
		return new ApplicationInstanceView(id,UUID.randomUUID(),ClientType.DESKTOP,VERSION,Instant.EPOCH,endedAt);
	}

	private static ApplicationInstanceView instance(long id,ClientType type,Instant endedAt) {
		return new ApplicationInstanceView(id,null,type,VERSION,Instant.EPOCH,endedAt);
	}
}
