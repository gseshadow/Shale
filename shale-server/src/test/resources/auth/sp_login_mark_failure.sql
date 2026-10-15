CREATE   PROC dbo.sp_login_mark_failure
  @email NVARCHAR(320)
AS
BEGIN
  SET NOCOUNT ON;

  DECLARE @max_attempts INT = (SELECT max_attempts FROM dbo.AuthConfig WHERE id = 1);
  DECLARE @lock_minutes INT = (SELECT lock_minutes FROM dbo.AuthConfig WHERE id = 1);
  IF @max_attempts IS NULL SET @max_attempts = 5;
  IF @lock_minutes IS NULL SET @lock_minutes = 15;

  UPDATE dbo.Users
  SET failed_attempts = failed_attempts + 1,
      lock_until = CASE
                      WHEN failed_attempts + 1 >= @max_attempts
                        THEN DATEADD(MINUTE, @lock_minutes, SYSUTCDATETIME())
                      ELSE lock_until
                   END
  WHERE LOWER(email)=LOWER(@email)
    AND (is_deleted = 0 OR is_deleted IS NULL);
END
