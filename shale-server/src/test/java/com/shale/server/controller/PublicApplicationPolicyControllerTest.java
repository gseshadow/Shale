package com.shale.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.shale.core.dto.*;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.model.*;
import com.shale.core.service.ApplicationReleaseReadServicePort;

class PublicApplicationPolicyControllerTest {
	private Service service; private MockMvc mvc;
	@BeforeEach void setup(){service=new Service();mvc=MockMvcBuilders.standaloneSetup(new PublicApplicationPolicyController(service)).setControllerAdvice(new ApiExceptionHandler()).build();}
	@Test void unauthenticatedReadUsesAuthoritativeServiceAndExposesOnlyNarrowGlobalFields() throws Exception {
		Instant time=Instant.parse("2026-09-30T10:15:30Z");
		service.policy=Optional.of(new ApplicationPolicyView(91,ReleaseChannel.PRODUCTION,7,new ReleaseReference(130,SemanticVersion.parse("1.0.130")),new ReleaseReference(129,SemanticVersion.parse("1.0.129")),new ReleaseReference(128,SemanticVersion.parse("1.0.128")),time,ApplicationAccessMode.READ_ONLY,time,time,new byte[]{1}));
		mvc.perform(get("/api/public/application-policy").param("channel","PRODUCTION"))
			.andExpect(status().isOk()).andExpect(header().string("Cache-Control","max-age=60, public"))
			.andExpect(jsonPath("$.channel").value("PRODUCTION")).andExpect(jsonPath("$.revision").value(7))
			.andExpect(jsonPath("$.recommendedVersion").value("1.0.129")).andExpect(jsonPath("$.minimumAllowedVersion").value("1.0.128"))
			.andExpect(jsonPath("$.deadline").value(time.toString())).andExpect(jsonPath("$.serverTime").value(time.toString()))
			.andExpect(content().string(not(containsString("tenant")))).andExpect(content().string(not(containsString("user"))))
			.andExpect(content().string(not(containsString("session")))).andExpect(content().string(not(containsString("accessMode"))))
			.andExpect(content().string(not(containsString("ReleaseId")))).andExpect(content().string(not(containsString("rowVersion"))));
		org.junit.jupiter.api.Assertions.assertEquals(ReleaseChannel.PRODUCTION,service.channel);
	}
	@Test void unknownChannelIsRejectedBeforeAuthorityRead() throws Exception {mvc.perform(get("/api/public/application-policy").param("channel","STABLE")).andExpect(status().isBadRequest());org.junit.jupiter.api.Assertions.assertNull(service.channel);}
	@Test void absentPolicyHasStablePublicNoContentSemantics() throws Exception {mvc.perform(get("/api/public/application-policy").param("channel","PILOT")).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","max-age=60, public"));}
	private static final class Service implements ApplicationReleaseReadServicePort {Optional<ApplicationPolicyView> policy=Optional.empty();ReleaseChannel channel;public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c){channel=c;return policy;}public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v){throw new AssertionError("public policy must not enumerate releases");}public List<ApplicationReleaseItemView> listReleaseItems(long id){throw new AssertionError("public policy must not enumerate release items");}}
}
