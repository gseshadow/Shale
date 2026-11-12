package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.*;
import com.shale.server.oauth.cimd.CimdMetadataService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Browser request/approval orchestration only. SQL remains the authoritative mutation/audit owner. */
final class CimdApplicationAccess {
    private final CimdServices services;
    private final CimdMetadataService metadata;
    private final CimdOAuthLifecycle lifecycle;
    private final Semaphore admission=new Semaphore(8);
    CimdApplicationAccess(CimdServices services,CimdMetadataService metadata,CimdOAuthLifecycle lifecycle) {
        this.services=services;this.metadata=metadata;this.lifecycle=lifecycle;
    }
    private AiOAuthExchangeStore.Owner owner() {
        var human=ShaleOAuthHuman.current();return new AiOAuthExchangeStore.Owner(human.tenant(),human.user());
    }
    Map<String,Object> request(String external,Set<AiScope> scopes) {
        if(!admission.tryAcquire())throw new IllegalStateException("Onboarding admission unavailable.");
        try {
            var id=new OAuthApplicationIdentifier(external);var snapshot=lifecycle.inspect(id);
            if(!snapshot.scopes().containsAll(scopes))throw new SecurityException("Registered scope ceiling required.");
            var projection=services.onboarding().apply(owner());
            var state=projection.find(external,ShaleOAuthHuman.current().session());
            int methods=(snapshot.methods().contains("none")?1:0)|(snapshot.methods().contains("private_key_jwt")?2:0);
            byte[] digest=HexFormat.of().parseHex(snapshot.revision());
            if(state.application()==null) {
                if(id.source()==OAuthApplicationIdentifier.Source.DCR)throw new SecurityException("Stored DCR registration required.");
                try {services.publisher().publishCimd(external,digest,methods,false,null);}
                catch(IllegalStateException raced){
                    // A single reload handles concurrent first publication; no identity/version guessing.
                    if(projection.find(external,ShaleOAuthHuman.current().session()).application()==null)throw raced;
                }
                state=projection.find(external,ShaleOAuthHuman.current().session());
            }
            if(state.application().disabled())throw new SecurityException("Application disabled.");
            if(!Arrays.equals(digest,state.digest())||methods!=state.methods()) {
                if(id.source()==OAuthApplicationIdentifier.Source.DCR)throw new SecurityException("Current immutable DCR revision required.");
                // Publishing validated changed bytes invalidates old tenant approvals immediately.
                // RowVer prevents racing a disablement from restoring a disabled application.
                try {services.publisher().publishCimd(external,digest,methods,false,state.application().rowVersion());}
                catch(IllegalStateException raced) {
                    var current=projection.find(external,ShaleOAuthHuman.current().session());
                    if(current.application()==null||current.application().disabled()||!Arrays.equals(current.digest(),digest)||current.methods()!=methods)throw raced;
                }
                state=projection.find(external,ShaleOAuthHuman.current().session());
            }
            lifecycle.prepare(id);
            if(state.approval()==null) {
                try {services.approvals().apply(owner()).request(state.application().applicationId(),state.application().rowVersion(),scopes);}
                catch(IllegalStateException raced) {
                    if(projection.find(external,ShaleOAuthHuman.current().session()).approval()==null)throw raced;
                }
                state=projection.find(external,ShaleOAuthHuman.current().session());
            }
            return view(snapshot,state,status(state));
        } finally {admission.release();}
    }
    Map<String,Object> decide(String external,UUID approval,byte[] expected,String decision,Set<AiScope> scopes) {
        var id=new OAuthApplicationIdentifier(external);var snapshot=lifecycle.inspect(id);
        if(!snapshot.scopes().containsAll(scopes))throw new SecurityException("Registered scope ceiling required.");
        var projection=services.onboarding().apply(owner());
        var state=projection.find(external,ShaleOAuthHuman.current().session());
        if(!state.admin()||state.approval()==null||!state.approval().approvalId().equals(approval))
            throw new SecurityException("Current same-tenant administrator required.");
        var selected=AiTenantApplicationService.Decision.valueOf(decision);
        if(selected==AiTenantApplicationService.Decision.APPROVE &&
                (!Arrays.equals(state.digest(),HexFormat.of().parseHex(snapshot.revision()))||state.application().disabled()))
            throw new SecurityException("Current publication review required.");
        services.approvals().apply(owner()).decide(approval,new AiIntegrationSecurityService.RowVersion(expected),selected,scopes,
                selected==AiTenantApplicationService.Decision.APPROVE?1:0);
        state=projection.find(external,ShaleOAuthHuman.current().session());
        return view(snapshot,state,status(state));
    }
    private static String status(AiCimdOnboardingStore.State state) {
        var approval=state.approval();
        if(approval==null)return "UNAPPROVED";
        if(approval.applicationRevision()!=state.application().revision()||!approval.expiresAt().isAfter(Instant.now()))return "APPROVAL_REVIEW_REQUIRED";
        return approval.state();
    }
    private static Map<String,Object> view(OAuthApplicationMetadata metadata,AiCimdOnboardingStore.State state,String status) {
        var human=ShaleOAuthHuman.current();var result=new LinkedHashMap<String,Object>();
        result.put("client_id",metadata.identifier().value());result.put("client_name",metadata.name());
        result.put("state",status);result.put("admin",state.admin());result.put("tenant",human.tenant());result.put("user",human.user());
        if(state.approval()!=null) {
            result.put("approval_state",state.approval().state());
            result.put("approval_id",state.approval().approvalId().toString());
            result.put("approval_version",HexFormat.of().formatHex(state.approval().rowVersion().bytes()));
        }
        return result;
    }
}
