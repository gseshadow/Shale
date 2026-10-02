package com.shale.data.service.adapter;

import java.time.*;
import java.util.*;
import com.shale.core.dto.*;
import com.shale.core.service.ApplicationInstanceAdminReadServicePort;
import com.shale.data.dao.ApplicationInstanceAdminReadDao;

public final class ApplicationInstanceAdminReadServiceAdapter implements ApplicationInstanceAdminReadServicePort {
	public static final int DEFAULT_PAGE_SIZE=50,MAX_PAGE_SIZE=100;
	public static final Duration DEFAULT_WINDOW=Duration.ofDays(30),MAX_WINDOW=Duration.ofDays(90),FUTURE_TOLERANCE=Duration.ofMinutes(5);
	private final Gateway gateway;private final Clock clock;
	public ApplicationInstanceAdminReadServiceAdapter(ApplicationInstanceAdminReadDao dao){this(new DaoGateway(dao),Clock.systemUTC());}
	ApplicationInstanceAdminReadServiceAdapter(Gateway gateway,Clock clock){this.gateway=Objects.requireNonNull(gateway,"gateway");this.clock=Objects.requireNonNull(clock,"clock");}
	@Override public ApplicationInstanceAdminPage listRecent(int tenant,int actor,Filter filter,int page,int size){validateScope(tenant,actor);if(page<0||page>100)throw new IllegalArgumentException("page must be between 0 and 100.");if(size<1||size>MAX_PAGE_SIZE)throw new IllegalArgumentException("size must be between 1 and 100.");Filter safe=filter==null?new Filter(null,null,null,false,defaultSince()):filter;Instant since=validateSince(safe.startedSince());if(safe.userId()!=null&&safe.userId()<=0)throw new IllegalArgumentException("userId must be positive.");return gateway.listRecent(tenant,actor,new Filter(safe.clientType(),safe.applicationVersion(),safe.userId(),safe.activeOnly(),since),page,size);}
	@Override public List<ApplicationVersionDistributionView> getVersionDistribution(int tenant,int actor,Instant since){validateScope(tenant,actor);return gateway.versionDistribution(tenant,actor,validateSince(since));}
	private Instant defaultSince(){return clock.instant().minus(DEFAULT_WINDOW);}
	private Instant validateSince(Instant since){Instant value=since==null?defaultSince():since,now=clock.instant();if(value.isBefore(now.minus(MAX_WINDOW))||value.isAfter(now.plus(FUTURE_TOLERANCE)))throw new IllegalArgumentException("since must be within the last 90 days and no more than five minutes in the future.");return value;}
	private static void validateScope(int tenant,int actor){if(tenant<=0||actor<=0)throw new SecurityException("An authenticated tenant administrator is required.");}
	interface Gateway { ApplicationInstanceAdminPage listRecent(int tenant,int actor,Filter filter,int page,int size);List<ApplicationVersionDistributionView> versionDistribution(int tenant,int actor,Instant since); }
	private record DaoGateway(ApplicationInstanceAdminReadDao dao) implements Gateway { DaoGateway{Objects.requireNonNull(dao,"dao");}public ApplicationInstanceAdminPage listRecent(int t,int a,Filter f,int p,int s){return dao.listRecent(t,a,f,p,s);}public List<ApplicationVersionDistributionView> versionDistribution(int t,int a,Instant since){return dao.versionDistribution(t,a,since);} }
}
