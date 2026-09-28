package com.shale.data.service.adapter;
import java.util.*;
import com.shale.core.dto.*;
import com.shale.core.model.*;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.data.dao.ApplicationReleaseReadDao;
public final class ApplicationReleaseReadServiceAdapter implements ApplicationReleaseReadServicePort {
	private final Gateway gateway;
	public ApplicationReleaseReadServiceAdapter(ApplicationReleaseReadDao dao){this(new DaoGateway(dao));}
	ApplicationReleaseReadServiceAdapter(Gateway gateway){this.gateway=Objects.requireNonNull(gateway,"gateway");}
	@Override public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel channel){return gateway.findCurrentPolicy(channel);}
	@Override public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel channel,SemanticVersion version){return gateway.listPublishedReleasesAfter(channel,version);}
	@Override public List<ApplicationReleaseItemView> listReleaseItems(long releaseId){return gateway.listReleaseItems(releaseId);}
	interface Gateway { Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c); List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v); List<ApplicationReleaseItemView> listReleaseItems(long id); }
	private record DaoGateway(ApplicationReleaseReadDao dao) implements Gateway {
		DaoGateway { Objects.requireNonNull(dao,"dao"); }
		public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c){return dao.findCurrentPolicy(c);}
		public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v){return dao.listPublishedReleasesAfter(c,v);}
		public List<ApplicationReleaseItemView> listReleaseItems(long id){return dao.listReleaseItems(id);}
	}
}
