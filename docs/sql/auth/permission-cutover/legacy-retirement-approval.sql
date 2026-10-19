IF N'$(LegacyRetirementApproval)'<>N'APPROVED' OR N'$(ReviewedIdentityMapping)'<>N'CONFIRMED'
 OR N'$(CallerInventoryApproved)'<>N'APPROVED' OR N'$(LegacyCallersDrained)'<>N'CONFIRMED'
 OR N'$(RuntimeLegacyDisposition)'<>N'DENY_THREE_MODULES'
 OR N'$(ExclusiveChangeWindow)'<>N'CONFIRMED'
 OR NULLIF(LTRIM(RTRIM(N'$(CallerInventoryEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(CallerDrainEvidence)')),N'') IS NULL
 OR NULLIF(LTRIM(RTRIM(N'$(CompatibilityEvidence)')),N'') IS NULL
 THROW 58343,N'Separate permission approval, identity mapping, inventory/drain/compatibility evidence and exclusive window required.',1;
-- References identify privately retained, reviewed evidence; strings cannot establish its truth.
GO
