package com.shale.server.controller;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.server.dto.PublicApplicationPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/** Cheap, unauthenticated read of the same global policy authority used by the authenticated desktop route. */
@RestController
@RequestMapping("/api/public/application-policy")
@SecurityRequirements
public final class PublicApplicationPolicyController {
	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
	private final ApplicationReleaseReadServicePort policies;
	public PublicApplicationPolicyController(ApplicationReleaseReadServicePort policies) { this.policies=policies; }
	@Operation(summary="Get public global application-update policy")
	@GetMapping
	public ResponseEntity<PublicApplicationPolicyResponse> current(@RequestParam String channel) {
		ReleaseChannel parsed=parse(channel);
		return policies.findCurrentPolicy(parsed).map(value->ResponseEntity.ok().cacheControl(CACHE).body(
				new PublicApplicationPolicyResponse(value.channel(),value.revisionNumber(),version(value.minimumRecommended()),
						version(value.minimumAllowed()),value.requiredUpdateDeadline(),value.serverTime())))
				.orElseGet(()->ResponseEntity.noContent().cacheControl(CACHE).build());
	}
	private static ReleaseChannel parse(String value){try{return ReleaseChannel.valueOf(value);}catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid release channel.");}}
	private static String version(ReleaseReference value){return value==null?null:value.version().toString();}
}
