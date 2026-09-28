package com.shale.core.service;
import java.util.List;
import java.util.Optional;
import com.shale.core.dto.*;
import com.shale.core.model.*;
public interface ApplicationReleaseReadServicePort {
	Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel channel);
	List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel channel, SemanticVersion exclusiveLowerBound);
	List<ApplicationReleaseItemView> listReleaseItems(long releaseId);
}
