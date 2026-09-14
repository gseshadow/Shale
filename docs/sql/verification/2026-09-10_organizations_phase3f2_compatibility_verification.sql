/*
 Organizations Phase 3F.2 operational verification. READ ONLY: no repair is performed.
 Run only as an approved administrator against an explicitly approved non-production target.
 Every result must report FindingCount = 0.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ExpectedDatabase sysname = N'Shale_Copy';
IF DB_NAME() <> @ExpectedDatabase
    THROW 51000, 'Refusing to inspect an unexpected database.', 1;
IF IS_SRVROLEMEMBER(N'sysadmin') <> 1 AND IS_MEMBER(N'db_owner') <> 1
    THROW 51000, 'An approved administrative verification context is required.', 1;

;WITH PreferredVoice AS (
 SELECT ShaleClientId,OrganizationId,DisplayNumber,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,OrganizationId ORDER BY IsPrimary DESC,SortOrder,Id) rn
 FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 AND Kind<>N'FAX'
), PreferredFax AS (
 SELECT ShaleClientId,OrganizationId,DisplayNumber,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,OrganizationId ORDER BY IsPrimary DESC,SortOrder,Id) rn
 FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 AND Kind=N'FAX'
), PreferredEmail AS (
 SELECT ShaleClientId,OrganizationId,EmailAddress,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,OrganizationId ORDER BY IsPrimary DESC,SortOrder,Id) rn
 FROM dbo.OrganizationEmailAddresses WHERE IsDeleted=0
), PreferredAddress AS (
 SELECT ShaleClientId,OrganizationId,AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,Country,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,OrganizationId ORDER BY IsPrimary DESC,SortOrder,Id) rn
 FROM dbo.OrganizationAddresses WHERE IsDeleted=0
), PreferredWebsite AS (
 SELECT ShaleClientId,OrganizationId,Website,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,OrganizationId ORDER BY IsPrimary DESC,SortOrder,Id) rn
 FROM dbo.OrganizationWebsites WHERE IsDeleted=0
), MirrorFindings AS (
 SELECT o.Id,
  CASE WHEN NULLIF(LTRIM(RTRIM(o.Phone)),N'') <> NULLIF(LTRIM(RTRIM(p.DisplayNumber)),N'') OR (NULLIF(LTRIM(RTRIM(o.Phone)),N'') IS NULL AND NULLIF(LTRIM(RTRIM(p.DisplayNumber)),N'') IS NOT NULL) OR (NULLIF(LTRIM(RTRIM(o.Phone)),N'') IS NOT NULL AND NULLIF(LTRIM(RTRIM(p.DisplayNumber)),N'') IS NULL) THEN 1 ELSE 0 END PhoneMismatch,
  CASE WHEN NULLIF(LTRIM(RTRIM(o.Fax)),N'') <> NULLIF(LTRIM(RTRIM(f.DisplayNumber)),N'') OR (NULLIF(LTRIM(RTRIM(o.Fax)),N'') IS NULL AND NULLIF(LTRIM(RTRIM(f.DisplayNumber)),N'') IS NOT NULL) OR (NULLIF(LTRIM(RTRIM(o.Fax)),N'') IS NOT NULL AND NULLIF(LTRIM(RTRIM(f.DisplayNumber)),N'') IS NULL) THEN 1 ELSE 0 END FaxMismatch,
  CASE WHEN NULLIF(LTRIM(RTRIM(o.Email)),N'') <> NULLIF(LTRIM(RTRIM(e.EmailAddress)),N'') OR (NULLIF(LTRIM(RTRIM(o.Email)),N'') IS NULL AND NULLIF(LTRIM(RTRIM(e.EmailAddress)),N'') IS NOT NULL) OR (NULLIF(LTRIM(RTRIM(o.Email)),N'') IS NOT NULL AND NULLIF(LTRIM(RTRIM(e.EmailAddress)),N'') IS NULL) THEN 1 ELSE 0 END EmailMismatch,
  CASE WHEN NULLIF(LTRIM(RTRIM(o.Website)),N'') <> NULLIF(LTRIM(RTRIM(w.Website)),N'') OR (NULLIF(LTRIM(RTRIM(o.Website)),N'') IS NULL AND NULLIF(LTRIM(RTRIM(w.Website)),N'') IS NOT NULL) OR (NULLIF(LTRIM(RTRIM(o.Website)),N'') IS NOT NULL AND NULLIF(LTRIM(RTRIM(w.Website)),N'') IS NULL) THEN 1 ELSE 0 END WebsiteMismatch,
  CASE WHEN (EXISTS(SELECT NULLIF(LTRIM(RTRIM(v)),N'') FROM (VALUES(o.Address1),(o.Address2),(o.City),(o.State),(o.PostalCode),(o.Country)) x(v) WHERE NULLIF(LTRIM(RTRIM(v)),N'') IS NOT NULL) AND a.OrganizationId IS NULL)
        OR (NOT EXISTS(SELECT NULLIF(LTRIM(RTRIM(v)),N'') FROM (VALUES(o.Address1),(o.Address2),(o.City),(o.State),(o.PostalCode),(o.Country)) x(v) WHERE NULLIF(LTRIM(RTRIM(v)),N'') IS NOT NULL) AND a.OrganizationId IS NOT NULL)
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.Address1)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.AddressLine1)),N''),N'')
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.Address2)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.AddressLine2)),N''),N'')
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.City)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.City)),N''),N'')
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.State)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.StateOrProvince)),N''),N'')
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.PostalCode)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.PostalCode)),N'') ,N'')
        OR ISNULL(NULLIF(LTRIM(RTRIM(o.Country)),N''),N'')<>ISNULL(NULLIF(LTRIM(RTRIM(a.Country)),N''),N'') THEN 1 ELSE 0 END AddressMismatch
 FROM dbo.Organizations o
 LEFT JOIN PreferredVoice p ON p.ShaleClientId=o.ShaleClientId AND p.OrganizationId=o.Id AND p.rn=1
 LEFT JOIN PreferredFax f ON f.ShaleClientId=o.ShaleClientId AND f.OrganizationId=o.Id AND f.rn=1
 LEFT JOIN PreferredEmail e ON e.ShaleClientId=o.ShaleClientId AND e.OrganizationId=o.Id AND e.rn=1
 LEFT JOIN PreferredAddress a ON a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND a.rn=1
 LEFT JOIN PreferredWebsite w ON w.ShaleClientId=o.ShaleClientId AND w.OrganizationId=o.Id AND w.rn=1
)
SELECT Finding, FindingCount FROM (
 SELECT N'Preferred voice/scalar Phone mismatch (including either side missing)' Finding,COALESCE(SUM(PhoneMismatch),0) FindingCount FROM MirrorFindings UNION ALL
 SELECT N'Preferred Fax/scalar Fax mismatch (including either side missing)',COALESCE(SUM(FaxMismatch),0) FROM MirrorFindings UNION ALL
 SELECT N'Preferred email/scalar Email mismatch (including either side missing)',COALESCE(SUM(EmailMismatch),0) FROM MirrorFindings UNION ALL
 SELECT N'Preferred address/scalar address mismatch (including either side missing)',COALESCE(SUM(AddressMismatch),0) FROM MirrorFindings UNION ALL
 SELECT N'Preferred website/scalar Website mismatch (including either side missing)',COALESCE(SUM(WebsiteMismatch),0) FROM MirrorFindings
) f;

SELECT Finding,FindingCount FROM (
 SELECT N'Multiple active phone primaries' Finding,COUNT_BIG(*) FindingCount FROM (SELECT ShaleClientId,OrganizationId FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Multiple active email primaries',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId FROM dbo.OrganizationEmailAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Multiple active address primaries',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId FROM dbo.OrganizationAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Multiple active website primaries',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId FROM dbo.OrganizationWebsites WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Invalid voice/Fax primary state',COUNT_BIG(*) FROM dbo.OrganizationPhoneNumbers p WHERE p.IsDeleted=0 AND p.Kind=N'FAX' AND p.IsPrimary=1 AND EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers v WHERE v.ShaleClientId=p.ShaleClientId AND v.OrganizationId=p.OrganizationId AND v.IsDeleted=0 AND v.Kind<>N'FAX') UNION ALL
 SELECT N'Duplicate active phone values',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId,Kind,DisplayNumber FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,Kind,DisplayNumber HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Duplicate active email values',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId,NormalizedEmail FROM dbo.OrganizationEmailAddresses WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,NormalizedEmail HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Duplicate active address values',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId,AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,Country FROM dbo.OrganizationAddresses WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,Country HAVING COUNT_BIG(*)>1) x UNION ALL
 SELECT N'Duplicate active website values',COUNT_BIG(*) FROM (SELECT ShaleClientId,OrganizationId,Website FROM dbo.OrganizationWebsites WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,Website HAVING COUNT_BIG(*)>1) x
) f;

SELECT Finding,FindingCount FROM (
 SELECT N'Cross-tenant or orphan phone rows' Finding,COUNT_BIG(*) FindingCount FROM dbo.OrganizationPhoneNumbers x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL UNION ALL
 SELECT N'Cross-tenant or orphan email rows',COUNT_BIG(*) FROM dbo.OrganizationEmailAddresses x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL UNION ALL
 SELECT N'Cross-tenant or orphan address rows',COUNT_BIG(*) FROM dbo.OrganizationAddresses x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL UNION ALL
 SELECT N'Cross-tenant or orphan website rows',COUNT_BIG(*) FROM dbo.OrganizationWebsites x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL UNION ALL
 SELECT N'Invalid ordering',COUNT_BIG(*) FROM (SELECT SortOrder,ROW_NUMBER() OVER(PARTITION BY SourceTable,ShaleClientId,OrganizationId ORDER BY SortOrder,Id)-1 ExpectedOrder FROM (SELECT N'phone' SourceTable,Id,ShaleClientId,OrganizationId,SortOrder,IsDeleted FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT N'email',Id,ShaleClientId,OrganizationId,SortOrder,IsDeleted FROM dbo.OrganizationEmailAddresses UNION ALL SELECT N'address',Id,ShaleClientId,OrganizationId,SortOrder,IsDeleted FROM dbo.OrganizationAddresses UNION ALL SELECT N'website',Id,ShaleClientId,OrganizationId,SortOrder,IsDeleted FROM dbo.OrganizationWebsites) q WHERE IsDeleted=0) x WHERE SortOrder<>ExpectedOrder OR SortOrder<0 UNION ALL
 SELECT N'Blank active rows',COUNT_BIG(*) FROM (SELECT DisplayNumber value,IsDeleted FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT EmailAddress,IsDeleted FROM dbo.OrganizationEmailAddresses UNION ALL SELECT Website,IsDeleted FROM dbo.OrganizationWebsites) x WHERE IsDeleted=0 AND NULLIF(LTRIM(RTRIM(value)),N'') IS NULL UNION ALL
 SELECT N'Blank active addresses',COUNT_BIG(*) FROM dbo.OrganizationAddresses WHERE IsDeleted=0 AND COALESCE(NULLIF(LTRIM(RTRIM(AddressLine1)),N''),NULLIF(LTRIM(RTRIM(AddressLine2)),N''),NULLIF(LTRIM(RTRIM(City)),N''),NULLIF(LTRIM(RTRIM(StateOrProvince)),N''),NULLIF(LTRIM(RTRIM(PostalCode)),N''),NULLIF(LTRIM(RTRIM(Country)),N''),NULLIF(LTRIM(RTRIM(LegacyAddressText)),N'')) IS NULL UNION ALL
 SELECT N'Deleted rows still primary',COUNT_BIG(*) FROM (SELECT IsDeleted,IsPrimary FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationEmailAddresses UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationAddresses UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationWebsites) x WHERE IsDeleted=1 AND IsPrimary=1
) f;
