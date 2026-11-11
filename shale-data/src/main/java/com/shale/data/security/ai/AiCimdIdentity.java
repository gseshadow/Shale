package com.shale.data.security.ai;

import java.util.Set;

/** Exact procedure capabilities. A role is never selected from a request or a failed SQL call. */
public enum AiCimdIdentity {
    CASE("AiCimdCaseRuntimeV1", "AiCimdDataAuthenticateV1", "AiCimdDataSearchCasesV1", "AiCimdDataGetCaseOverviewV1", "AiCimdDataRecordCaseFailureV1", "AiCimdDataFinishCaseQuotaV1", "AiCimdDataAuthorizeCaseHandoffV1"),
    BROWSER("AiCimdOAuthBrowserV1", "AiCimdLoadClientV1", "AiCimdBeginOAuthV1", "AiCimdFindOAuthIntentV1", "AiCimdApproveOAuthV1", "AiCimdCompleteOAuthV1", "AiCimdDenyOAuthV1"),
    ISSUER("AiCimdOAuthIssuerV1", "AiCimdLoadClientProofV1", "AiCimdRedeemV1", "AiCimdRefreshV1", "AiCimdRevokeCredentialV1"),
    RESOLVER("AiCimdOAuthResolverV1", "AiCimdResolveCodeV1", "AiCimdResolveRefreshV1", "AiCimdResolveRevocationV1"),
    METADATA("AiCimdMetadataPublisherV1", "AiCimdResolvePublicationV1", "AiCimdPublishMetadataV1"),
    NEUTRAL("AiCimdOAuthNeutralV1", "AiCimdRevokeUnknownV1"),
    REGISTRY_PUBLISHER("AiApplicationPublisher", "AiPublishApplicationV1"),
    REGISTRY_TENANT("AiApplicationTenant", "AiRequestApplicationV1", "AiDecideApplicationV1"),
    CATALOG("AiCimdCatalogV1", "AiCimdAttestCatalogV1");

    private final String role;
    private final Set<String> roots;
    AiCimdIdentity(String role, String... roots) { this.role=role; this.roots=Set.of(roots); }
    public String role() { return role; }
    public Set<String> roots() { return roots; }
    public String key() { return name().toLowerCase(java.util.Locale.ROOT).replace('_','-'); }
    public boolean tenantScoped() { return Set.of(BROWSER, ISSUER, REGISTRY_TENANT).contains(this); }
}
