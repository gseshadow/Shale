/*
 * READ ONLY. Detect active protected-semantic singleton conflicts for reviewed repair.
 * This script does not infer identity from labels or dates and changes no data.
 */
SET NOCOUNT ON;

;WITH protected_occurrences AS
(
 SELECT DISTINCT cd.ShaleClientId, cd.CaseId, cd.Id, cd.CaseDateTypeId, m.SemanticRoleKey
 FROM dbo.CaseDates AS cd
 JOIN dbo.CaseDateTypeSemanticRoleMappings AS m
   ON m.CaseDateTypeId = cd.CaseDateTypeId
  AND (m.ShaleClientId = cd.ShaleClientId OR m.ShaleClientId IS NULL)
 WHERE cd.IsDeleted = 0
   AND m.SemanticRoleKey IN ('INTAKE', 'STATUTE_OF_LIMITATIONS', 'TORT_NOTICE_DEADLINE')
)
SELECT cd.ShaleClientId,
       cd.CaseId,
       cd.SemanticRoleKey,
       COUNT(DISTINCT cd.Id) AS ActiveOccurrenceCount,
       STRING_AGG(CONVERT(varchar(max), cd.Id), ',') WITHIN GROUP (ORDER BY cd.Id) AS CaseDateIds,
       STRING_AGG(CONVERT(varchar(max), CONCAT(cd.Id, ':', cd.CaseDateTypeId)), ',') WITHIN GROUP (ORDER BY cd.Id) AS CaseDateIdTypeIdPairs
FROM protected_occurrences AS cd
GROUP BY cd.ShaleClientId, cd.CaseId, cd.SemanticRoleKey
HAVING COUNT(DISTINCT cd.Id) > 1
ORDER BY cd.ShaleClientId, cd.CaseId, cd.SemanticRoleKey;
