CREATE   PROC dbo.sp_login_get_hash
  @email NVARCHAR(320)
AS
BEGIN
  SET NOCOUNT ON;

  DECLARE @now DATETIME2 = SYSUTCDATETIME();

  /* Block if locked */
  IF EXISTS (
    SELECT 1 FROM dbo.Users
    WHERE LOWER(email)=LOWER(@email)
      AND (is_deleted = 0 OR is_deleted IS NULL)
      AND lock_until IS NOT NULL
      AND lock_until > @now
  )
  BEGIN
    -- Intentionally return no rows, caller treats as invalid
    RETURN;
  END

  /* Return hash only */
  SELECT TOP (1) password_hash
  FROM dbo.Users
  WHERE LOWER(email)=LOWER(@email)
    AND (is_deleted = 0 OR is_deleted IS NULL);
END
