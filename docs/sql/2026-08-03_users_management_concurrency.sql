-- Adds the authoritative optimistic-concurrency and update timestamp columns used by User Management.
IF COL_LENGTH('dbo.Users', 'UpdatedAt') IS NULL
  ALTER TABLE dbo.Users ADD UpdatedAt datetime2(7) NULL;
IF COL_LENGTH('dbo.Users', 'RowVer') IS NULL
  ALTER TABLE dbo.Users ADD RowVer rowversion NOT NULL;
