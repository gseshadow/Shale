-- Explicit alternative only; these acknowledgements never certify fleet completeness or zero invocations.
-- Trusted fixed literals and safe private change-control references only. Review records independently.
IF N'$(LegacyAcceptanceRoute)'<>N'CONTROLLED_CHANGE'
 OR N'$(LegacyRetirementApproval)'<>N'APPROVED'
 OR N'$(ReviewedIdentityMapping)'<>N'CONFIRMED'
 OR N'$(RuntimeLegacyDisposition)'<>N'DENY_THREE_MODULES'
 OR N'$(ExclusiveChangeWindow)'<>N'CONFIRMED'
 OR N'$(CompletedPackageReview)'<>N'APPROVED'
 OR N'$(ControlledWindowAuthorization)'<>N'AUTHORIZED'
 OR N'$(UnknownClientRisk)'<>N'ACCEPTED_NOT_ZERO_PROOF'
 OR N'$(IrrecoverableEffectsRisk)'<>N'ACCEPTED'
 OR N'$(ReopenedCapabilityRisk)'<>N'ACCEPTED'
 OR N'$(RepresentativeChecks)'<>N'PASSED'
 OR N'$(RollbackRehearsal)'<>N'PASSED'
 THROW 58360,N'Explicit controlled route, package/window approval and bounded risk/check/rehearsal decisions required; no zero-invocation proof.',1;
IF NULLIF(LTRIM(RTRIM(N'$(SecurityOwner)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(ServiceOwner)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(RiskApprovalEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(PackageReviewEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(WindowAuthorizationEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(KnownArtifactEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(CompatibilityEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(OperationalReadinessEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(RollbackRehearsalEvidence)')),N'') IS NULL
 THROW 58360,N'Named security/service owners and reviewed risk/package/window/artifact/compatibility/operations/rehearsal references required.',1;
-- Nonempty references record decisions; they cannot validate their truth or authorize themselves.
GO
