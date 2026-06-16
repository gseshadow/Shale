/*
  Enforce tenant-scoped normalized-email uniqueness for dbo.Users.

  The Add User flow blocks duplicates in application code for both active and
  inactive users so inactive accounts can be reactivated instead of duplicated.
  This unique index is the database backstop for that rule.
*/

IF COL_LENGTH('dbo.Users', 'ShaleClientId') IS NULL
    THROW 52601, 'dbo.Users.ShaleClientId does not exist.', 1;

IF COL_LENGTH('dbo.Users', 'email_norm') IS NULL
    THROW 52602, 'dbo.Users.email_norm does not exist.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Users
    WHERE email_norm IS NOT NULL
      AND LTRIM(RTRIM(email_norm)) <> ''
    GROUP BY ShaleClientId, email_norm
    HAVING COUNT(*) > 1
)
    THROW 52603, 'Duplicate dbo.Users rows exist by (ShaleClientId, email_norm). Resolve duplicates before creating UX_Users_ShaleClientId_EmailNorm.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Users')
      AND name = N'UX_Users_ShaleClientId_EmailNorm'
)
BEGIN
    CREATE UNIQUE INDEX UX_Users_ShaleClientId_EmailNorm
        ON dbo.Users (ShaleClientId, email_norm)
        WHERE email_norm IS NOT NULL
          AND email_norm <> '';
END;
