CREATE   PROC dbo.sp_login_mark_success
  @email NVARCHAR(320)
AS
BEGIN
  SET NOCOUNT ON;

  UPDATE dbo.Users
  SET failed_attempts = 0,
      lock_until = NULL,
      last_login = SYSUTCDATETIME()
  WHERE LOWER(email)=LOWER(@email)
    AND (is_deleted = 0 OR is_deleted IS NULL);
END
