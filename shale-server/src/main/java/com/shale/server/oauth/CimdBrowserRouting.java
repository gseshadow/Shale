package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;

/** Exact request namespace selection. Consent/code namespaces never fall back to each other. */
final class CimdBrowserRouting implements AiOAuthBrowserStore {
    private final AiOAuthBrowserStore legacy;
    private final CimdServices services;
    private final CimdOAuthLifecycle lifecycle;
    private final Supplier<HttpServletRequest> request;
    CimdBrowserRouting(AiOAuthBrowserStore legacy,CimdServices services,CimdOAuthLifecycle lifecycle,Supplier<HttpServletRequest> request) {
        this.legacy=legacy;this.services=services;this.lifecycle=lifecycle;this.request=request;
    }
    @Override public boolean externalIdentity(){return CimdOAuthProviders.external(request.get().getParameter("client_id"));}
    private AiOAuthBrowserStore delegate() {
        if(!externalIdentity())return legacy;
        new OAuthApplicationIdentifier(request.get().getParameter("client_id"));
        var human=ShaleOAuthHuman.current();
        return lifecycle.browser(services.browser().apply(new AiOAuthExchangeStore.Owner(human.tenant(),human.user())));
    }
    private void client(String value){if(!Objects.equals(value,request.get().getParameter("client_id")))throw new SecurityException("Exact browser client required.");}
    @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String id){client(id);return delegate().loadClient(id);}
    @Override public void begin(UUID id,UUID web,String client,String redirect,String challenge,String state,Set<AiScope> scopes,String nonce){client(client);delegate().begin(id,web,client,redirect,challenge,state,scopes,nonce);}
    @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce,UUID web){return delegate().findIntent(nonce,web);}
    @Override public void approve(UUID id,UUID web,Set<AiScope> scopes){delegate().approve(id,web,scopes);}
    @Override public void complete(UUID id,UUID web,Set<AiScope> scopes,String code){delegate().complete(id,web,scopes,code);}
    @Override public void deny(UUID id,UUID web){delegate().deny(id,web);}
}
