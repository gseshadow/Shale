package com.shale.server.controller;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

import com.shale.core.dto.*;
import com.shale.core.model.*;
import com.shale.core.service.ApplicationReleaseImportServicePort;
import com.shale.server.auth.ReleaseControlPlaneAuthorizer;
import com.shale.server.dto.ApplicationReleaseImportRequest;
import com.shale.data.dao.ApplicationReleaseImportDao.ReleaseImportConflictException;

@RestController
@RequestMapping("/api/control-plane/application-releases")
public final class ApplicationReleaseImportController {
	private static final List<String> GROUPS=List.of("New","Improvements","Fixes");
	private final ApplicationReleaseImportServicePort service; private final ReleaseControlPlaneAuthorizer auth;
	public ApplicationReleaseImportController(ApplicationReleaseImportServicePort service,ReleaseControlPlaneAuthorizer auth){this.service=service;this.auth=auth;}
	@PostMapping("/{version}/import")
	public ResponseEntity<ApplicationReleaseImportResult> importRelease(@PathVariable String version,
			@RequestHeader(value="X-Shale-Control-Plane-Token",required=false) String token,
			@RequestBody ApplicationReleaseImportRequest request){
		String operator=auth.require(token);
		try {
			SemanticVersion path=SemanticVersion.parse(version), body=SemanticVersion.parse(request.version());
			if(!path.equals(body))throw new IllegalArgumentException("Release version does not match request path.");
			if(request.groups()==null||!request.groups().keySet().equals(Set.copyOf(GROUPS)))throw new IllegalArgumentException("Release groups must be New, Improvements, and Fixes.");
			List<ApplicationReleaseImport.Item> items=new ArrayList<>();int order=0;
			for(String group:GROUPS)for(String text:request.groups().get(group))items.add(new ApplicationReleaseImport.Item(order++,group,type(group),text));
			LocalDate date=request.releaseDate()==null?null:LocalDate.parse(request.releaseDate());
			byte[] rowVer=request.expectedRowVersion()==null?null:Base64.getDecoder().decode(request.expectedRowVersion());
			var command=new ApplicationReleaseImport(body,request.title(),date,request.summary(),items,rowVer);
			return ResponseEntity.ok(service.importProductionRelease(command,Boolean.TRUE.equals(request.allowUpdate()),operator));
		}catch(ReleaseImportConflictException ex){throw new ResponseStatusException(CONFLICT,ex.getMessage());}
		catch(RuntimeException ex){if(ex instanceof ResponseStatusException r)throw r;throw new ResponseStatusException(BAD_REQUEST,"Invalid structured release notes.");}
	}
	private static ReleaseItemType type(String group){return switch(group){case"New"->ReleaseItemType.FEATURE;case"Improvements"->ReleaseItemType.IMPROVEMENT;case"Fixes"->ReleaseItemType.FIX;default->throw new IllegalArgumentException();};}
}
