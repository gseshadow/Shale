package com.shale.data.service.adapter;

import java.util.*;
import com.shale.core.dto.UserReleaseStateView;
import com.shale.core.model.*;
import com.shale.core.service.UserReleaseStateServicePort;
import com.shale.data.dao.UserReleaseStateDao;

public final class UserReleaseStateServiceAdapter implements UserReleaseStateServicePort {
	private final Gateway gateway;
	public UserReleaseStateServiceAdapter(UserReleaseStateDao dao){this(new DaoGateway(dao));}
	UserReleaseStateServiceAdapter(Gateway gateway){this.gateway=Objects.requireNonNull(gateway,"gateway");}
	public Optional<UserReleaseStateView> findCurrent(int t,int u,ClientType c,ReleaseChannel r){return gateway.findCurrent(t,u,c,r);}
	public UserReleaseStateView acknowledge(int t,int u,ClientType c,ReleaseChannel r,long id,byte[] rv){return gateway.acknowledge(t,u,c,r,id,rv);}
	interface Gateway {Optional<UserReleaseStateView> findCurrent(int t,int u,ClientType c,ReleaseChannel r);UserReleaseStateView acknowledge(int t,int u,ClientType c,ReleaseChannel r,long id,byte[] rv);}
	private record DaoGateway(UserReleaseStateDao dao) implements Gateway {DaoGateway{Objects.requireNonNull(dao,"dao");}public Optional<UserReleaseStateView> findCurrent(int t,int u,ClientType c,ReleaseChannel r){return dao.findCurrent(t,u,c,r);}public UserReleaseStateView acknowledge(int t,int u,ClientType c,ReleaseChannel r,long id,byte[] rv){return dao.acknowledge(t,u,c,r,id,rv);}}
}
