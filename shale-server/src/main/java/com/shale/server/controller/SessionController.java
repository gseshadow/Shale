package com.shale.server.controller;

import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.shale.core.model.ClientType;
import com.shale.server.dto.*;
import com.shale.server.runtime.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/api/sessions") @Tag(name="Sessions",description="Authenticated self-service durable-session management") @SecurityRequirement(name="bearerAuth")
public final class SessionController {
	private final SessionManagementService service;private final ServerRuntimeSessionState state;private final ShaleAuthTokenService tokens;
	public SessionController(SessionManagementService service,ServerRuntimeSessionState state,ShaleAuthTokenService tokens){this.service=service;this.state=state;this.tokens=tokens;}
	@Operation(summary="List my durable sessions") @GetMapping public List<UserSessionResponse> list(HttpServletRequest request){var p=state.requirePrincipal();return service.listOwnSessions(p,sid(request));}
	@Operation(summary="Revoke my current durable session") @PostMapping("/current/revoke") @ResponseStatus(HttpStatus.NO_CONTENT) public void current(HttpServletRequest request){var p=state.requirePrincipal();UUID sid=sid(request);service.revokeCurrentSession(p,sid);}
	@Operation(summary="Revoke one of my durable sessions") @PostMapping("/{sessionId}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT) public void one(@PathVariable(name="sessionId") UUID sessionId,HttpServletRequest request){var p=state.requirePrincipal();service.revokeOwnSession(p,sid(request),sessionId);}
	@Operation(summary="Revoke all of my other durable sessions") @PostMapping("/revoke-others") public Map<String,Integer> others(HttpServletRequest request){var p=state.requirePrincipal();return Map.of("revokedCount",service.revokeOtherOwnSessions(p,sid(request)));}
	private UUID sid(HttpServletRequest request){String raw=BearerTokenServerSessionResolver.bearerToken(request);if(raw==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication is required.");return tokens.verifyToken(raw).filter(VerifiedAuthToken::isSessionBound).map(VerifiedAuthToken::sessionId).map(UUID::fromString).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"A bound session is required."));}
}
