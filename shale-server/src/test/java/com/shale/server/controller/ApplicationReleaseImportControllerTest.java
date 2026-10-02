package com.shale.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.shale.core.dto.*;
import com.shale.core.service.ApplicationReleaseImportServicePort;
import com.shale.server.auth.ReleaseControlPlaneAuthorizer;

class ApplicationReleaseImportControllerTest {
	private Recording service; private MockMvc mvc; private static final String TOKEN="01234567890123456789012345678901";
	@BeforeEach void setup(){service=new Recording();mvc=MockMvcBuilders.standaloneSetup(new ApplicationReleaseImportController(service,new ReleaseControlPlaneAuthorizer(TOKEN,"release-pipeline"))).setControllerAdvice(new ApiExceptionHandler()).build();}
	@Test void authorizedInitialImportPreservesGroupAndGlobalOrder()throws Exception{
		mvc.perform(post("/api/control-plane/application-releases/1.2.3/import").header("X-Shale-Control-Plane-Token",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json("1.2.3")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CREATED"));
		Assertions.assertEquals(List.of("New:FEATURE:0","Improvements:IMPROVEMENT:1","Fixes:FIX:2"),service.command.items().stream().map(i->i.group()+":"+i.type()+":"+i.sortOrder()).toList());
		Assertions.assertEquals("release-pipeline",service.operator);
	}
	@Test void missingOrWrongControlPlaneCredentialIsRejectedBeforeMutation()throws Exception{
		mvc.perform(post("/api/control-plane/application-releases/1.2.3/import").contentType(MediaType.APPLICATION_JSON).content(json("1.2.3"))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/control-plane/application-releases/1.2.3/import").header("X-Shale-Control-Plane-Token","wrong").contentType(MediaType.APPLICATION_JSON).content(json("1.2.3"))).andExpect(status().isUnauthorized());
		Assertions.assertEquals(0,service.calls);
	}
	@Test void versionMismatchIsRejected()throws Exception{mvc.perform(post("/api/control-plane/application-releases/1.2.4/import").header("X-Shale-Control-Plane-Token",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json("1.2.3"))).andExpect(status().isBadRequest());Assertions.assertEquals(0,service.calls);}
	private static String json(String v){return "{\"version\":\""+v+"\",\"title\":\"What is new\",\"releaseDate\":\"2026-10-02\",\"summary\":\"Summary\",\"groups\":{\"New\":[\"A\"],\"Improvements\":[\"B\"],\"Fixes\":[\"C\"]}}";}
	private static final class Recording implements ApplicationReleaseImportServicePort{int calls;ApplicationReleaseImport command;String operator;public ApplicationReleaseImportResult importProductionRelease(ApplicationReleaseImport c,boolean update,String op){calls++;command=c;operator=op;return new ApplicationReleaseImportResult(1,c.version().toString(),ApplicationReleaseImportResult.Outcome.CREATED,new byte[]{1});}}
}
