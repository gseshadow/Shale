/*
  PracticeAreas modularization pass 2 (explicit built-in mapping).

  Goals:
  - Assign explicit SystemKey identities for the current tenant 7 built-ins.
  - Keep current tenant rows active and unchanged in ownership scope.
  - Avoid rewriting Cases.PracticeAreaId history.
  - Defer global/default seeding in this pass.

  Explicit built-ins:
  - Medical Malpractice -> medical_malpractice
  - Personal Injury     -> personal_injury
  - Sexual Assault      -> sexual_assault
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL
BEGIN
    THROW 51310, 'dbo.PracticeAreas does not exist.', 1;
END;

IF COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL
BEGIN
    THROW 51311, 'dbo.PracticeAreas.SystemKey does not exist. Run phase 1 first.', 1;
END;

-- Normalize existing keys before explicit mapping.
UPDATE pa
SET SystemKey = LOWER(LTRIM(RTRIM(pa.SystemKey)))
FROM dbo.PracticeAreas pa
WHERE pa.SystemKey IS NOT NULL
  AND pa.SystemKey <> LOWER(LTRIM(RTRIM(pa.SystemKey)));

/*
  Map explicit built-ins on tenant 7 rows only.
  We intentionally set keys on matching tenant rows and do not alter ShaleClientId,
  IsActive/IsDeleted, or any case FK references.
*/
UPDATE pa
SET SystemKey = 'medical_malpractice'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Medical Malpractice'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'medical_malpractice');

UPDATE pa
SET SystemKey = 'personal_injury'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Personal Injury'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'personal_injury');

UPDATE pa
SET SystemKey = 'sexual_assault'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Sexual Assault'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'sexual_assault');

-- Diagnostics for this pass (read-only, optional):
-- SELECT Id, ShaleClientId, Name, SystemKey, IsActive, IsDeleted
-- FROM dbo.PracticeAreas
-- WHERE ShaleClientId = 7
--   AND Name IN ('Medical Malpractice', 'Personal Injury', 'Sexual Assault')
-- ORDER BY Name, Id;
