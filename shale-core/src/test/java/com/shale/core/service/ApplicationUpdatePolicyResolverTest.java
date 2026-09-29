package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.ApplicationPolicyView;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.model.*;

class ApplicationUpdatePolicyResolverTest {
	private final ApplicationUpdatePolicyResolver resolver=new ApplicationUpdatePolicyResolver();
	private static final Instant DEADLINE=Instant.parse("2026-10-05T00:00:00Z");
	@Test void currentIncludesEqualAndVersionsAheadOfEveryTarget(){assertEquals(ApplicationUpdatePolicyState.CURRENT,resolve("1.0.130",policy("1.0.130","1.0.127"),DEADLINE));assertEquals(ApplicationUpdatePolicyState.CURRENT,resolve("2.0.0",policy("1.0.130","1.0.127"),DEADLINE));}
	@Test void belowRecommendedButAtLeastAllowedIsRecommended(){assertEquals(ApplicationUpdatePolicyState.RECOMMENDED,resolve("1.0.128",policy("1.0.130","1.0.127"),DEADLINE));}
	@Test void requiredDeadlineUsesAuthoritativeTimeAtExactBoundary(){var p=policy("1.0.130","1.0.129");assertEquals(ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE,resolve("1.0.128",p,DEADLINE.minusNanos(1)));assertEquals(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED,resolve("1.0.128",p,DEADLINE));assertEquals(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED,resolve("1.0.128",p,DEADLINE.plusNanos(1)));}
	@Test void unknownProtectsMissingTimePolicyAndChannelMismatch(){var p=policy("1.0.130","1.0.129");assertEquals(ApplicationUpdatePolicyState.UNKNOWN,resolve("1.0.128",p,null));assertEquals(ApplicationUpdatePolicyState.UNKNOWN,resolver.resolve(SemanticVersion.parse("1.0.128"),ReleaseChannel.PILOT,p,DEADLINE));assertEquals(ApplicationUpdatePolicyState.UNKNOWN,resolver.resolve(SemanticVersion.parse("1.0.128"),ReleaseChannel.PRODUCTION,null,DEADLINE));}
	@Test void correctedPolicyImmediatelyRemovesRequirement(){assertEquals(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED,resolve("1.0.128",policy("1.0.130","1.0.129"),DEADLINE));assertEquals(ApplicationUpdatePolicyState.CURRENT,resolve("1.0.128",policy("1.0.128","1.0.128"),DEADLINE));}
	private ApplicationUpdatePolicyState resolve(String running,ApplicationPolicyView p,Instant now){return resolver.resolve(SemanticVersion.parse(running),ReleaseChannel.PRODUCTION,p,now);}
	private static ApplicationPolicyView policy(String recommended,String allowed){return new ApplicationPolicyView(1,ReleaseChannel.PRODUCTION,7,ref("1.0.130"),ref(recommended),ref(allowed),DEADLINE,ApplicationAccessMode.NORMAL,Instant.EPOCH,Instant.EPOCH,new byte[]{1});}
	private static ReleaseReference ref(String version){return new ReleaseReference(1,SemanticVersion.parse(version));}
}
