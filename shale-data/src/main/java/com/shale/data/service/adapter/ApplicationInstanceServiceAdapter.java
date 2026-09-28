package com.shale.data.service.adapter;

import java.util.Objects;
import java.util.UUID;
import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationInstanceServicePort;
import com.shale.data.dao.ApplicationInstanceDao;

public final class ApplicationInstanceServiceAdapter implements ApplicationInstanceServicePort {
	private final Gateway gateway;
	public ApplicationInstanceServiceAdapter(ApplicationInstanceDao dao){this(new DaoGateway(dao));}
	ApplicationInstanceServiceAdapter(Gateway gateway){this.gateway=Objects.requireNonNull(gateway,"gateway");}
	@Override public ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v){return gateway.enroll(t,u,m,c,v);}
	@Override public ApplicationInstanceView end(int t,int u,long id){return gateway.end(t,u,id);}
	interface Gateway{ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v);ApplicationInstanceView end(int t,int u,long id);}
	private record DaoGateway(ApplicationInstanceDao dao) implements Gateway{DaoGateway{Objects.requireNonNull(dao,"dao");}public ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v){return dao.enroll(t,u,m,c,v);}public ApplicationInstanceView end(int t,int u,long id){return dao.end(t,u,id);}}
}
