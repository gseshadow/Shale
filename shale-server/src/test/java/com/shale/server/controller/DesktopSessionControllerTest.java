package com.shale.server.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.http.MediaType;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.shale.core.dto.UserSessionView;import com.shale.core.model.*;import com.shale.core.result.Result;import com.shale.core.service.AuthServicePort;
import com.shale.server.runtime.*;

class DesktopSessionControllerTest {
	@Test void nullInstanceCreatesAnUnlinkedDesktopSession()throws Exception{
		var auth=new Auth();var store=new Store();var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());
		var service=new ServerAuthSessionService(tokens,store,new DurableSessionTokenValidator(store),new LegacyTokenCompatibilityPolicy(Instant.now(),3600,Clock.systemUTC()),new InMemoryTokenRevocationStore());
		var mvc=MockMvcBuilders.standaloneSetup(new DesktopSessionController(auth,service,new Verifier(true))).setControllerAdvice(new ApiExceptionHandler()).build();
		mvc.perform(post("/api/auth/desktop-session").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@test\",\"password\":\"secret\"}"))
				.andExpect(status().isOk());
		assertEquals(1,store.creates);assertNull(store.instance);
	}
 @Test void verifiedCredentialsCreateExactlyOneDesktopSessionBoundToOwnedInstance()throws Exception{
  var auth=new Auth();var store=new Store();var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());
  var service=new ServerAuthSessionService(tokens,store,new DurableSessionTokenValidator(store),new LegacyTokenCompatibilityPolicy(Instant.now(),3600,Clock.systemUTC()),new InMemoryTokenRevocationStore());
  var verifier=new Verifier(true);var mvc=MockMvcBuilders.standaloneSetup(new DesktopSessionController(auth,service,verifier)).setControllerAdvice(new ApiExceptionHandler()).build();
  String body=mvc.perform(post("/api/auth/desktop-session").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@test\",\"password\":\"secret\",\"applicationInstanceId\":44}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").isString()).andExpect(jsonPath("$.currentJti").isString()).andReturn().getResponse().getContentAsString();
  assertEquals(1,store.creates);assertEquals(ClientType.DESKTOP,store.type);assertEquals(44L,store.instance);assertTrue(tokens.verifyToken(com.jayway.jsonpath.JsonPath.read(body,"$.accessToken")).orElseThrow().isSessionBound());
 }
 @Test void invalidCredentialsAndForeignInstanceFailWithoutSessionAndClaimsCannotBeSupplied()throws Exception{
  var auth=new Auth();var store=new Store();var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());var service=new ServerAuthSessionService(tokens,store,new DurableSessionTokenValidator(store),new LegacyTokenCompatibilityPolicy(Instant.now(),3600,Clock.systemUTC()),new InMemoryTokenRevocationStore());
  var mvc=MockMvcBuilders.standaloneSetup(new DesktopSessionController(auth,service,new Verifier(false))).build();
  mvc.perform(post("/api/auth/desktop-session").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@test\",\"password\":\"secret\",\"applicationInstanceId\":99,\"shaleClientId\":8,\"userId\":999}")) .andExpect(status().isForbidden());assertEquals(0,store.creates);
  auth.ok=false;mvc.perform(post("/api/auth/desktop-session").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@test\",\"password\":\"wrong\"}")) .andExpect(status().isUnauthorized());assertEquals(0,store.creates);
 }
 static final class Auth implements AuthServicePort{boolean ok=true;public Result<User> authenticate(String e,String p){return ok?Result.ok(User.builder().id(9).shaleClientId(7).email("owner@test").build()):Result.fail("no");}}
 static final class Verifier extends DesktopApplicationInstanceVerifier{final boolean result;Verifier(boolean r){super(p->{throw new AssertionError();});result=r;}@Override public boolean isAttachable(ServerPrincipal p,long id){assertEquals(7,p.shaleClientId());assertEquals(9,p.userId());return result;}}
 static final class Store implements DurableSessionStore{int creates;ClientType type;Long instance;UserSessionView value;public UserSessionView create(ServerPrincipal p,ClientType c,Long i,UUID j,Instant e){creates++;type=c;instance=i;return value=new UserSessionView(1,UUID.randomUUID(),c,i,j,Instant.now(),e,null,null,null);}public Optional<UserSessionView> find(ServerPrincipal p,UUID s){return Optional.ofNullable(value);}public UserSessionView rotate(ServerPrincipal p,UUID s,UUID x,UUID n,Instant e){throw new UnsupportedOperationException();}public UserSessionView revoke(ServerPrincipal p,UUID s,String r){throw new UnsupportedOperationException();}}
}
