package com.shale.server.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationInstanceServicePort;
import com.shale.server.dto.*;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController @RequestMapping("/api/application-instances")
@Tag(name="Application Instances",description="Authenticated client-process enrollment; not user sessions")
@SecurityRequirement(name="bearerAuth")
public final class ApplicationInstanceController {
	private final ApplicationInstanceServicePort service; private final ServerRuntimeSessionState sessions;
	public ApplicationInstanceController(ApplicationInstanceServicePort service,ServerRuntimeSessionState sessions){this.service=service;this.sessions=sessions;}
	@Operation(summary="Enroll this authenticated desktop process")
	@ApiResponses({@ApiResponse(responseCode="200",description="Enrolled instance",content=@Content(schema=@Schema(implementation=ApplicationInstanceResponse.class))),@ApiResponse(responseCode="400",description="Invalid UUID, client type, or semantic version",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),@ApiResponse(responseCode="401",description="Authentication required",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),@ApiResponse(responseCode="500",description="Safe internal error",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))})
	@PostMapping public ApplicationInstanceResponse enroll(@Valid @RequestBody ApplicationInstanceEnrollmentRequest request){ServerPrincipal p=sessions.requirePrincipal();try{UUID machine=UUID.fromString(request.machineId());ClientType type=ClientType.valueOf(request.clientType());if(type!=ClientType.DESKTOP)throw new IllegalArgumentException();SemanticVersion version=SemanticVersion.parse(request.applicationVersion());return ApplicationInstanceResponse.from(service.enroll(p.shaleClientId(),p.userId(),machine,type,version));}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enrollment requires a canonical UUID, DESKTOP client type, and canonical major.minor.build version.");}}
	@Operation(summary="End this authenticated owner's application instance",description="Idempotently records the first server-observed end time; it never deletes the row or remotely ends another user's instance.")
	@ApiResponses({@ApiResponse(responseCode="200",description="Ended or already-ended instance",content=@Content(schema=@Schema(implementation=ApplicationInstanceResponse.class))),@ApiResponse(responseCode="401",description="Authentication required",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),@ApiResponse(responseCode="403",description="Instance is outside the authenticated owner boundary",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),@ApiResponse(responseCode="404",description="Instance does not exist or is not visible",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),@ApiResponse(responseCode="500",description="Safe internal error",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))})
	@PostMapping("/{id}/end") public ApplicationInstanceResponse end(@PathVariable long id){ServerPrincipal p=sessions.requirePrincipal();try{return ApplicationInstanceResponse.from(service.end(p.shaleClientId(),p.userId(),id));}catch(SecurityException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application instance was not found.");}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Application instance id must be positive.");}}
}
