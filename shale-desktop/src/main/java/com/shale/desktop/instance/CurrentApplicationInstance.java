package com.shale.desktop.instance;

import java.util.Optional;
import com.shale.core.dto.ApplicationInstanceView;

/** Process-local enrollment state. It is deliberately neither durable nor an auth/session store. */
public final class CurrentApplicationInstance {
	private volatile ApplicationInstanceView value;
	public Optional<ApplicationInstanceView> get(){return Optional.ofNullable(value);}
	public void set(ApplicationInstanceView value){this.value=java.util.Objects.requireNonNull(value,"value");}
	public void clear(){value=null;}
}
