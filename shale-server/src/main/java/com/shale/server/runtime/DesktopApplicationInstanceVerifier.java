package com.shale.server.runtime;

import java.sql.SQLException;
import java.util.Objects;

import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.data.dao.ApplicationInstanceDao;

/** Validates the optional Phase 7C desktop-session link without changing instance lifecycle state. */
public class DesktopApplicationInstanceVerifier {
	private final OwnedInstanceLookup instances;

	public DesktopApplicationInstanceVerifier(RuntimeConnectionProvider connections) {
		this((principal,id) -> new ApplicationInstanceDao(() -> open(connections,principal))
				.requireOwned(principal.shaleClientId(),principal.userId(),id));
	}

	DesktopApplicationInstanceVerifier(OwnedInstanceLookup instances) {
		this.instances=Objects.requireNonNull(instances,"instances");
	}

	public boolean isAttachable(ServerPrincipal principal,long applicationInstanceId) {
		Objects.requireNonNull(principal,"principal");
		if(applicationInstanceId<=0)return false;
		try {
			ApplicationInstanceView instance=instances.requireOwned(principal,applicationInstanceId);
			return instance.clientType()==ClientType.DESKTOP&&instance.endedAt()==null;
		} catch(SecurityException | IllegalArgumentException ex) {
			return false;
		}
	}

	private static java.sql.Connection open(RuntimeConnectionProvider connections,ServerPrincipal principal) {
		try{return Objects.requireNonNull(connections,"connections").openConnection(principal);}
		catch(SQLException ex){throw new IllegalStateException("Failed to open application-instance verification connection",ex);}
	}

	@FunctionalInterface
	interface OwnedInstanceLookup {
		ApplicationInstanceView requireOwned(ServerPrincipal principal,long applicationInstanceId);
	}
}
