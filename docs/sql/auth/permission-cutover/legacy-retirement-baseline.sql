:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
SELECT N'LegacyRetirementBaseline' AS CheckName,CONVERT(varchar(66),StateHash,1) AS StateHash FROM #LegacyFingerprint;
GO
