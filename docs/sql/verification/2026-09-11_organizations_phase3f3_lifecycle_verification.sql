/* Read-only Organizations Phase 3F.3 lifecycle verification. Do not use this script to repair data. */
SET NOCOUNT ON;

SELECT ShaleClientId, DeletedOrganizationCount=COUNT_BIG(*)
FROM dbo.Organizations WHERE ISNULL(IsDeleted,0)=1 GROUP BY ShaleClientId ORDER BY ShaleClientId;

SELECT DeletedOrganizationsWithActiveCaseRelationshipsFindingCount=COUNT_BIG(DISTINCT o.Id)
FROM dbo.Organizations o JOIN dbo.CaseOrganizations co ON co.OrganizationId=o.Id
JOIN dbo.Cases c ON c.Id=co.CaseId AND c.ShaleClientId=o.ShaleClientId AND ISNULL(c.IsDeleted,0)=0
WHERE ISNULL(o.IsDeleted,0)=1;

SELECT TypeCompatibilityMismatchFindingCount=COUNT_BIG(*) FROM dbo.Organizations o
WHERE ISNULL(o.IsDeleted,0)=1 AND (SELECT COUNT(*) FROM dbo.OrganizationOrganizationTypes a WHERE a.OrganizationId=o.Id AND a.ShaleClientId=o.ShaleClientId AND a.IsDeleted=0 AND a.IsPrimary=1 AND a.OrganizationTypeId=o.OrganizationTypeId)<>1;

SELECT StructuredMirrorMismatchFindingCount=COUNT_BIG(*) FROM dbo.Organizations o
OUTER APPLY(SELECT TOP(1) p.DisplayNumber FROM dbo.OrganizationPhoneNumbers p WHERE p.OrganizationId=o.Id AND p.ShaleClientId=o.ShaleClientId AND p.IsDeleted=0 AND p.Kind<>'FAX' ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) phone
OUTER APPLY(SELECT TOP(1) p.DisplayNumber FROM dbo.OrganizationPhoneNumbers p WHERE p.OrganizationId=o.Id AND p.ShaleClientId=o.ShaleClientId AND p.IsDeleted=0 AND p.Kind='FAX' ORDER BY p.SortOrder,p.Id) fax
OUTER APPLY(SELECT TOP(1) e.EmailAddress FROM dbo.OrganizationEmailAddresses e WHERE e.OrganizationId=o.Id AND e.ShaleClientId=o.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) email
OUTER APPLY(SELECT TOP(1) w.Website FROM dbo.OrganizationWebsites w WHERE w.OrganizationId=o.Id AND w.ShaleClientId=o.ShaleClientId AND w.IsDeleted=0 ORDER BY w.IsPrimary DESC,w.SortOrder,w.Id) web
WHERE ISNULL(o.IsDeleted,0)=1 AND (ISNULL(o.Phone,N'')<>ISNULL(phone.DisplayNumber,N'') OR ISNULL(o.Fax,N'')<>ISNULL(fax.DisplayNumber,N'') OR ISNULL(o.Email,N'')<>ISNULL(email.EmailAddress,N'') OR ISNULL(o.Website,N'')<>ISNULL(web.Website,N''));

SELECT CrossTenantOrOrphanChildFindingCount=SUM(FindingCount) FROM (
 SELECT COUNT_BIG(*) FindingCount FROM dbo.OrganizationOrganizationTypes x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL
 UNION ALL SELECT COUNT_BIG(*) FROM dbo.OrganizationPhoneNumbers x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL
 UNION ALL SELECT COUNT_BIG(*) FROM dbo.OrganizationEmailAddresses x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL
 UNION ALL SELECT COUNT_BIG(*) FROM dbo.OrganizationAddresses x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL
 UNION ALL SELECT COUNT_BIG(*) FROM dbo.OrganizationWebsites x LEFT JOIN dbo.Organizations o ON o.Id=x.OrganizationId AND o.ShaleClientId=x.ShaleClientId WHERE o.Id IS NULL
) findings;

/* The confirmed deployed Organizations schema has no DeletedAt/DeletedByUserId columns. */
SELECT RestoredOrganizationsRetainingDeletionMetadataFindingCount=CONVERT(bigint,0), ActiveOrganizationsRetainingDeletionMetadataFindingCount=CONVERT(bigint,0);
SELECT DuplicateOrganizationIdentityFindingCount=COUNT_BIG(*) FROM (SELECT Id FROM dbo.Organizations GROUP BY Id HAVING COUNT(*)>1) duplicates;
SELECT ShaleClientId, RestorationAuditCount=COUNT_BIG(*) FROM dbo.EntityActionAuditLog WHERE EntityType='ORGANIZATION' AND Action='RESTORED' GROUP BY ShaleClientId ORDER BY ShaleClientId;
