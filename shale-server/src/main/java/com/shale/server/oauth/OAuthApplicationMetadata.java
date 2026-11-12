package com.shale.server.oauth;

import com.shale.server.oauth.cimd.CimdMetadataService;
import com.shale.core.security.ai.AiScope;
import java.time.Instant;
import java.util.*;

/** One validated source-specific publication. No tenant, approval, consent or credential authority. */
public record OAuthApplicationMetadata(String issuer, OAuthApplicationIdentifier identifier, String name,
        List<String> redirects, Set<String> methods, Set<String> grants, List<CimdMetadataService.SigningKey> keys,
        String revision, Instant expiresAt, Set<AiScope> scopes) {
    public OAuthApplicationMetadata {
        redirects=List.copyOf(redirects);methods=Set.copyOf(methods);grants=Set.copyOf(grants);
        keys=List.copyOf(keys);scopes=Set.copyOf(scopes);
    }
    static OAuthApplicationMetadata cimd(CimdMetadataService.Snapshot value) {
        return new OAuthApplicationMetadata(value.issuer(),new OAuthApplicationIdentifier(value.identifier().value()),value.name(),
                value.redirects(),value.methods(),value.grants(),value.keys(),value.revision(),value.expiresAt(),AiScope.fromMask(3));
    }
    @Override public String toString(){return "OAuthApplicationMetadata[redacted]";}
}
