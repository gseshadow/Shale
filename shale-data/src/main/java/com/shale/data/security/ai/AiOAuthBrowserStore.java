package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import java.util.*;

/** Durable browser contract. Both implementations retain their own SQL identity/transaction namespace. */
public interface AiOAuthBrowserStore {
    boolean externalIdentity();
    Optional<AiOAuthTransactionStore.Client> loadClient(String clientId);
    void begin(UUID id, UUID session, String client, String redirect, String challenge, String state, Set<AiScope> scopes, String nonce);
    Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce, UUID session);
    void approve(UUID id, UUID session, Set<AiScope> scopes);
    void complete(UUID id, UUID session, Set<AiScope> scopes, String code);
    void deny(UUID id, UUID session);
}
