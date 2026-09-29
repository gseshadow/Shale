package com.shale.server.controller;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.shale.core.model.*;
import com.shale.core.service.ApplicationInstanceAdminReadServicePort;
import com.shale.core.service.ApplicationInstanceAdminReadServicePort.Filter;
import com.shale.server.auth.CurrentUserProfileService;
import com.shale.server.dto.*;
import com.shale.server.runtime.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/admin/application-instances")
@Tag(name="Application Instance Administration",description="Read-only, tenant-scoped application-instance visibility for tenant administrators")
@SecurityRequirement(name="bearerAuth")
public final class ApplicationInstanceAdminController {
	private final ApplicationInstanceAdminReadServicePort service;private final ServerRuntimeSessionState sessions;private final CurrentUserProfileService profiles;
	public ApplicationInstanceAdminController(ApplicationInstanceAdminReadServicePort service,ServerRuntimeSessionState sessions,CurrentUserProfileService profiles){this.service=service;this.sessions=sessions;this.profiles=profiles;}
	@Operation(summary="List recent application instances",description="Tenant-admin-only offset page ordered by startedAt descending then applicationInstanceId descending. Null lifecycle timestamps remain null. Machine ID is a random grouping UUID, not a hardware fingerprint, secret, or proof of trust.")
	@ApiResponses({@ApiResponse(responseCode="200",description="Bounded recent-instance page"),@ApiResponse(responseCode="400",description="Invalid filter or pagination"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="403",description="Tenant administrator required")})
	@GetMapping public PagedResponse<AdminApplicationInstanceResponse> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)String clientType,@RequestParam(required=false)String version,@RequestParam(required=false)Integer userId,@RequestParam(defaultValue="false")boolean activeOnly,@RequestParam(required=false)String since){ServerPrincipal p=requireAdmin();try{Filter f=new Filter(parseClientType(clientType),parseVersion(version),userId,activeOnly,parseSince(since));var result=service.listRecent(p.shaleClientId(),p.userId(),f,page,size);return new PagedResponse<>(result.items().stream().map(AdminApplicationInstanceResponse::from).toList(),result.page(),result.size(),null);}catch(IllegalArgumentException ex){throw badRequest(ex);}}
	@Operation(summary="Get recent instance version distribution",description="Groups tenant process launches whose startedAt is in the bounded window by numeric semantic-version components. instanceCount is not a unique-user count; distinctUserCount is separately deduplicated by user ID.")
	@ApiResponses({@ApiResponse(responseCode="200",description="Numeric descending version distribution"),@ApiResponse(responseCode="400",description="Invalid time window"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="403",description="Tenant administrator required")})
	@GetMapping("/version-distribution") public List<ApplicationVersionDistributionResponse> distribution(@RequestParam(required=false)String since){ServerPrincipal p=requireAdmin();try{return service.getVersionDistribution(p.shaleClientId(),p.userId(),parseSince(since)).stream().map(ApplicationVersionDistributionResponse::from).toList();}catch(IllegalArgumentException ex){throw badRequest(ex);}}
	private ServerPrincipal requireAdmin(){ServerPrincipal p=sessions.requirePrincipal();var user=profiles.findCurrentUser(p).orElseThrow(()->forbidden());if(!user.isAdmin()||user.shaleClientId()!=p.shaleClientId()||user.userId()!=p.userId())throw forbidden();return p;}
	private static ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access is required.");}
	private static ResponseStatusException badRequest(IllegalArgumentException e){return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
	private static ClientType parseClientType(String value){if(value==null||value.isBlank())return null;return ClientType.valueOf(value);}
	private static SemanticVersion parseVersion(String value){return value==null||value.isBlank()?null:SemanticVersion.parse(value);}
	private static Instant parseSince(String value){if(value==null||value.isBlank())return null;try{return Instant.parse(value);}catch(DateTimeParseException e){throw new IllegalArgumentException("since must be an ISO-8601 UTC instant.");}}
}
