# Shale Database Schema Reference

*Last updated: 2026-06-15*

This document is the working schema reference for Codex and Shale development prompts.

Source of truth for this version: live schema output from the Shale database.

**Important:** Before adding or changing SQL, verify column names against the live database or existing DAO/query code. Do not introduce new column names based on memory or naming guesses.

---

## Development Tenant

Common development tenant:

| Item                | Value          |
| ------------------- | -------------- |
| ShaleClientId       | `7`            |
| Tenant display name | `Curtis & Co.` |
| City                | `Albuquerque`  |
| State               | `NM`           |

---

## Tenancy / Row Level Security

Shale uses tenant isolation by `ShaleClientId`.

Runtime DB connections should stamp SQL Server `SESSION_CONTEXT` before tenant-scoped queries.

Relevant context keys:

| Key              | Purpose                         |
| ---------------- | ------------------------------- |
| `ShaleClientId`  | Tenant isolation key            |
| `PrincipalEmail` | Optional principal/user context |

Typical runtime pattern:

```sql
EXEC sys.sp_set_session_context
    @key = N'ShaleClientId',
    @value = @TenantId,
    @read_only = 1;
```

General rules:

* Preserve tenant filtering.
* Preserve RLS behavior.
* Do not bypass tenant isolation.
* Do not open runtime tenant-scoped connections without tenant context.

---

# Core Tables

---

## dbo.ShaleClients

Tenant/client table.

| Column        | Notes               |
| ------------- | ------------------- |
| `Id`          | Primary key         |
| `DisplayName` | Tenant display name |
| `City`        | City                |
| `State`       | State               |
| `Postal`      | Postal code         |
| `IsActive`    | Active flag         |

---

## dbo.Users

Application users.

| Column                 | Type          | Notes                           |
| ---------------------- | ------------- | ------------------------------- |
| `id`                   | int           | Primary key                     |
| `name_first`           | nvarchar(100) | First name                      |
| `name_last`            | nvarchar(100) | Last name                       |
| `email`                | nvarchar(510) | Email address                   |
| `email_norm`           | nvarchar(510) | Normalized email                |
| `password_hash`        | nvarchar(510) | Password hash                   |
| `password_alg`         | varchar(20)   | Usually `bcrypt`                |
| `color`                | nvarchar(100) | User color                      |
| `is_attorney`          | bit           | Attorney flag                   |
| `is_admin`             | bit           | Admin flag                      |
| `is_deleted`           | bit           | Soft delete flag                |
| `default_organization` | nvarchar(200) | Default organization            |
| `organization_id`      | int           | Organization reference          |
| `initials`             | nvarchar(20)  | User initials                   |
| `created_at`           | datetime      | Created timestamp               |
| `password_salt`        | varbinary(16) | Legacy/alternate password field |
| `password_iters`       | int           | Password iterations             |
| `last_login`           | datetime2     | Last login timestamp            |
| `failed_attempts`      | int           | Login failure count             |
| `lock_until`           | datetime2     | Lockout timestamp               |
| `ShaleClientId`        | int           | Tenant id                       |
| `LegacyUserId`         | int           | Legacy import id                |
| `Phone`                | nvarchar(100) | Phone number                    |

Common display expression:

```sql
LTRIM(RTRIM(CONCAT(u.name_first, ' ', u.name_last)))
```

---

## dbo.Cases

Primary case table.

| Column                               | Type          | Notes                                 |
| ------------------------------------ | ------------- | ------------------------------------- |
| `Id`                                 | int           | Primary key                           |
| `Name`                               | nvarchar(max) | Case name                             |
| `CallerTime`                         | time          | Intake/caller time                    |
| `CallerDate`                         | date          | Intake/caller date                    |
| `AcceptedDate`                       | date          | Accepted date                         |
| `ClosedDate`                         | date          | Closed date                           |
| `DeniedDate`                         | date          | Denied date                           |
| `PracticeAreaId`                     | int           | Practice area id                      |
| `CaseNumber`                         | nvarchar(200) | Case number                           |
| `ClientEstate`                       | bit           | Client estate flag                    |
| `OfficePrinterCode`                  | nvarchar(200) | Office printer code                   |
| `FollowUpMeetWithClient`             | bit           | Follow-up flag                        |
| `FollowUpNurseReview`                | bit           | Follow-up flag                        |
| `FollowUpExpertReview`               | bit           | Follow-up flag                        |
| `FollowUpCaseTransferred`            | bit           | Follow-up flag                        |
| `AcceptedChronology`                 | bit           | Accepted workflow flag                |
| `AcceptedConsultantExpertSearch`     | bit           | Accepted workflow flag                |
| `AcceptedTestifyingExpertSearch`     | bit           | Accepted workflow flag                |
| `AcceptedMedicalLiterature`          | bit           | Accepted workflow flag                |
| `AcceptedDetail`                     | nvarchar(max) | Accepted detail                       |
| `DeniedChronology`                   | bit           | Denied workflow flag                  |
| `DeniedDetail`                       | nvarchar(max) | Denied detail                         |
| `FeeAgreementSigned`                 | bit           | Fee agreement flag                    |
| `DateFeeAgreementSigned`             | date          | Fee agreement signed date             |
| `ReceivedUpdates`                    | nvarchar(max) | Received updates text                 |
| `IsDeleted`                          | bit           | Soft delete flag                      |
| `DateOfMedicalNegligence`            | date          | Date of medical negligence            |
| `DateMedicalNegligenceWasDiscovered` | date          | Discovery date for medical negligence |
| `DateOfInjury`                       | date          | Date of incident/injury               |
| `StatuteOfLimitations`               | date          | SOL deadline                          |
| `TortNoticeDeadline`                 | date          | Tort claims notice deadline           |
| `DiscoveryDeadline`                  | date          | Discovery deadline                    |
| `MedicalRecordsRequested`             | bit           | Medical records requested flag         |
| `Description`                        | nvarchar(max) | Main case description                 |
| `Summary`                            | nvarchar(max) | Case summary                          |
| `CreatedAt`                          | datetime2     | Created timestamp                     |
| `UpdatedAt`                          | datetime2     | Updated timestamp                     |
| `ShaleClientId`                      | int           | Tenant id                             |
| `RowVer`                             | timestamp     | Row version                           |
| `NonEngagementLetterSent`            | bit           | Non-engagement letter flag            |
| `DateNonEngagementLetterSent`        | date          | Non-engagement letter date            |

### Confirmed case grid fields

| UI Field                    | Source                           |
| --------------------------- | -------------------------------- |
| Case Name                   | `dbo.Cases.Name`                 |
| Intake Date                 | `dbo.Cases.CallerDate`           |
| Description                 | `dbo.Cases.Description`          |
| Summary                     | `dbo.Cases.Summary`              |
| Date of Incident            | `dbo.Cases.DateOfInjury`         |
| Statute of Limitations      | `dbo.Cases.StatuteOfLimitations` |
| Tort Claims Notice Deadline | `dbo.Cases.TortNoticeDeadline`   |
| Discovery Deadline          | `dbo.Cases.DiscoveryDeadline`    |

### Do not use these as confirmed dbo.Cases columns

The live schema does **not** include these columns:

| Invalid/Unconfirmed Column | Note                                                                                      |
| -------------------------- | ----------------------------------------------------------------------------------------- |
| `IncidentDescription`      | Use `Description` instead.                                                                |
| `IncidentOccurred`         | Use `DateOfInjury` for current Date of Incident.                                          |
| `CaseStatusId`             | Not present in the live `dbo.Cases` output. Do not use unless a future migration adds it. |
| `CasePracticeAreaId`       | Not present in the live `dbo.Cases` output.                                               |

### Case status warning

`dbo.Statuses` exists, but the current live `dbo.Cases` schema output does **not** include `CaseStatusId`.

Do not write new SQL assuming:

```sql
c.CaseStatusId
```

If case status is needed, trace how the current app derives or displays it and reuse that verified mechanism. If no verified mechanism exists, return blank/null rather than crashing.

---

## dbo.Statuses

Status lookup table.

| Column          | Type          | Notes               |
| --------------- | ------------- | ------------------- |
| `Id`            | int           | Primary key         |
| `ShaleClientId` | int           | Tenant id, nullable |
| `Name`          | nvarchar(100) | Status name         |
| `IsClosed`      | bit           | Closed flag         |
| `SortOrder`     | int           | Sort order          |
| `Color`         | nvarchar(20)  | Display color       |
| `LifecycleKey`  | nvarchar(64)  | Lifecycle key       |
| `SystemKey`     | nvarchar(128) | System key          |

Important: status table exists, but no confirmed FK from `dbo.Cases` appears in the current live schema output.

---

## dbo.Roles

Role lookup table for case users/contacts.

| Column          | Type          | Notes             |
| --------------- | ------------- | ----------------- |
| `Id`            | int           | Primary key       |
| `Name`          | nvarchar(200) | Role name         |
| `Description`   | nvarchar(510) | Role description  |
| `IsActive`      | bit           | Active flag       |
| `CreatedAt`     | datetime2     | Created timestamp |
| `UpdatedAt`     | datetime2     | Updated timestamp |
| `ShaleClientId` | int           | Tenant id         |

Known role ids from prior role seed output:

|  Id | Name                   | Description                  |
| --: | ---------------------- | ---------------------------- |
| `1` | `client`               | Primary client/contact       |
| `2` | `caller`               | Initial caller               |
| `3` | `judge`                | Assigned judge               |
| `4` | `responsible_attorney` | Responsible attorney         |
| `5` | `prelitigation`        | Intake/pre-litigation person |

Verify role seed data before adding new role-dependent SQL.

---

## dbo.CaseUsers

Associates users with cases.

| Column      | Type           | Notes                       |
| ----------- | -------------- | --------------------------- |
| `Id`        | int            | Primary key                 |
| `CaseId`    | int            | Reference to `dbo.Cases.Id` |
| `UserId`    | int            | Reference to `dbo.Users.id` |
| `RoleId`    | int            | Role id from `dbo.Roles.Id` |
| `IsPrimary` | bit            | Primary flag                |
| `Notes`     | nvarchar(2000) | Notes                       |
| `CreatedAt` | datetime2      | Created timestamp           |
| `UpdatedAt` | datetime2      | Updated timestamp           |

Important: `CaseUsers` uses `RoleId`, not `Role`.

Responsible attorney lookup pattern:

```sql
OUTER APPLY (
    SELECT TOP (1)
        cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id
      AND cu.RoleId = 4
    ORDER BY
        cu.IsPrimary DESC,
        cu.UpdatedAt DESC,
        cu.Id DESC
) raLink
LEFT JOIN dbo.Users ra
    ON ra.id = raLink.UserId
```

---

## dbo.CaseContacts

Associates contacts with cases.

| Column      | Type           | Notes                          |
| ----------- | -------------- | ------------------------------ |
| `CaseId`    | int            | Reference to `dbo.Cases.Id`    |
| `ContactId` | int            | Reference to `dbo.Contacts.Id` |
| `Side`      | nvarchar(60)   | Contact side                   |
| `IsPrimary` | bit            | Primary flag                   |
| `Notes`     | nvarchar(2000) | Notes                          |
| `AddedAt`   | datetime2      | Added timestamp                |
| `CreatedAt` | datetime2      | Created timestamp              |
| `UpdatedAt` | datetime2      | Updated timestamp              |
| `RowVer`    | timestamp      | Row version                    |
| `Role`      | int            | Role id from `dbo.Roles.Id`    |

Important: `CaseContacts` uses `Role`, not `RoleId`.

Client lookup pattern:

```sql
OUTER APPLY (
    SELECT TOP (1)
        cc.ContactId
    FROM dbo.CaseContacts cc
    WHERE cc.CaseId = c.Id
      AND cc.Role = 1
    ORDER BY
        cc.IsPrimary DESC,
        cc.UpdatedAt DESC,
        cc.ContactId DESC
) clientLink
LEFT JOIN dbo.Contacts client
    ON client.Id = clientLink.ContactId
```

Opposing counsel role id is not confirmed in this reference. Verify `dbo.Roles` seed data before writing SQL that depends on opposing counsel role.

---

## dbo.Contacts

Contacts table.

| Column           | Type          | Notes                  |
| ---------------- | ------------- | ---------------------- |
| `Id`             | int           | Primary key            |
| `Name`           | nvarchar(max) | Display/full name      |
| `CreatedAt`      | datetime2     | Created timestamp      |
| `UpdatedAt`      | datetime2     | Updated timestamp      |
| `FirstName`      | nvarchar(max) | First name             |
| `LastName`       | nvarchar(max) | Last name              |
| `WorkName`       | nvarchar(max) | Work name              |
| `DateOfBirth`    | date          | Date of birth          |
| `Description`    | nvarchar(max) | Description            |
| `Condition`      | nvarchar(max) | Condition              |
| `Notes`          | nvarchar(max) | Notes                  |
| `PhoneCell`      | nvarchar(max) | Cell phone             |
| `PhoneHome`      | nvarchar(max) | Home phone             |
| `PhoneWork`      | nvarchar(max) | Work phone             |
| `AddressHome`    | nvarchar(max) | Home address           |
| `AddressWork`    | nvarchar(max) | Work address           |
| `AddressOther`   | nvarchar(max) | Other address          |
| `EmailPersonal`  | nvarchar(max) | Personal email         |
| `EmailWork`      | nvarchar(max) | Work email             |
| `EmailOther`     | nvarchar(max) | Other email            |
| `IsClient`       | bit           | Client flag            |
| `IsExpert`       | bit           | Expert flag            |
| `IsDeleted`      | bit           | Soft delete flag       |
| `IsDeceased`     | bit           | Deceased flag          |
| `OrganizationId` | int           | Organization reference |
| `ImageVersion`   | int           | Image version          |
| `ReferredFrom`   | nvarchar(max) | Referral source        |
| `ReferralType`   | nvarchar(max) | Referral type          |
| `RowVer`         | timestamp     | Row version            |
| `ShaleClientId`  | int           | Tenant id              |

Contact display fallback:

```sql
COALESCE(
    NULLIF(LTRIM(RTRIM(c.Name)), ''),
    NULLIF(LTRIM(RTRIM(CONCAT(c.FirstName, ' ', c.LastName))), ''),
    NULLIF(LTRIM(RTRIM(c.WorkName)), '')
)
```

---

## dbo.Organizations

Organizations table.

| Column               | Type           | Notes             |
| -------------------- | -------------- | ----------------- |
| `Id`                 | int            | Primary key       |
| `ShaleClientId`      | int            | Tenant id         |
| `OrganizationTypeId` | int            | Organization type |
| `Name`               | nvarchar(400)  | Organization name |
| `Phone`              | nvarchar(60)   | Phone             |
| `Fax`                | nvarchar(60)   | Fax               |
| `Email`              | nvarchar(508)  | Email             |
| `Website`            | nvarchar(600)  | Website           |
| `Address1`           | nvarchar(400)  | Address line 1    |
| `Address2`           | nvarchar(400)  | Address line 2    |
| `City`               | nvarchar(200)  | City              |
| `State`              | nvarchar(100)  | State             |
| `PostalCode`         | nvarchar(40)   | Postal code       |
| `Country`            | nvarchar(200)  | Country           |
| `Notes`              | nvarchar(4000) | Notes             |
| `IsDeleted`          | bit            | Soft delete flag  |
| `CreatedAt`          | datetime2      | Created timestamp |
| `UpdatedAt`          | datetime2      | Updated timestamp |
| `RowVer`             | timestamp      | Row version       |

---

## dbo.CaseUpdates

Case notes/updates.

| Column            | Type          | Notes                       |
| ----------------- | ------------- | --------------------------- |
| `Id`              | bigint        | Primary key                 |
| `CaseId`          | int           | Reference to `dbo.Cases.Id` |
| `ShaleClientId`   | int           | Tenant id                   |
| `NoteText`        | nvarchar(max) | Update/comment text         |
| `CreatedAt`       | datetime2     | Created timestamp           |
| `CreatedByUserId` | int           | Reference to `dbo.Users.id` |
| `UpdatedAt`       | datetime2     | Updated timestamp           |
| `EditedByUserId`  | int           | Editor user id              |
| `RowVersion`      | timestamp     | Row version                 |
| `IsDeleted`       | bit           | Soft delete flag            |
| `DeletedAt`       | datetime2     | Deleted timestamp           |
| `DeletedByUserId` | int           | Deleted by user id          |

Latest case update pattern:

```sql
OUTER APPLY (
    SELECT TOP (1)
        cu.NoteText,
        cu.CreatedAt,
        cu.CreatedByUserId
    FROM dbo.CaseUpdates cu
    WHERE cu.CaseId = c.Id
      AND cu.ShaleClientId = c.ShaleClientId
      AND ISNULL(cu.IsDeleted, 0) = 0
      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
    ORDER BY
        cu.CreatedAt DESC,
        cu.Id DESC
) latestUpdate
```

Created-by user pattern:

```sql
LEFT JOIN dbo.Users updateUser
    ON updateUser.id = latestUpdate.CreatedByUserId
```

---

# Case Grid Reference

## Default columns

| UI Column                   | Source                                                                                     |
| --------------------------- | ------------------------------------------------------------------------------------------ |
| Case Name                   | `dbo.Cases.Name`                                                                           |
| Client                      | `dbo.CaseContacts` Role `1` + `dbo.Contacts` display name                                  |
| Intake Date                 | `dbo.Cases.CallerDate`                                                                     |
| Case Status                 | No confirmed `dbo.Cases` status FK in live schema. Trace existing app behavior before use. |
| Opposing Parties            | `dbo.CaseParties` role `party` + side `opposing` + `dbo.Contacts` display names            |
| Latest Case Update          | Latest non-deleted `dbo.CaseUpdates.NoteText`                                              |
| Description                 | `dbo.Cases.Description`                                                                    |
| Date of Incident            | `dbo.Cases.DateOfInjury`                                                                   |
| Statute of Limitations      | `dbo.Cases.StatuteOfLimitations`                                                           |
| Tort Claims Notice Deadline | `dbo.Cases.TortNoticeDeadline`                                                             |
| Responsible Attorney        | `dbo.CaseUsers.RoleId = 4` + `dbo.Users`                                                   |

## Latest Case Update

This should never display boolean or numeric placeholder values like `false` or `0`.

Use only:

```sql
dbo.CaseUpdates.NoteText
```

with:

```sql
ISNULL(IsDeleted, 0) = 0
NULLIF(LTRIM(RTRIM(NoteText)), '') IS NOT NULL
ORDER BY CreatedAt DESC, Id DESC
```

## Case Status

Case status should eventually be available as:

* Visible/selectable grid column
* Sort option
* Filter option

But current live `dbo.Cases` output does not include `CaseStatusId`.

Do not implement case status by guessing. Reuse a verified existing mechanism or leave blank/null until the schema relationship is confirmed.

---

# Known Legacy / Migration Notes

Older singular table names existed during migration but should not be used for new work unless intentionally working on migration code:

* `dbo.Case`
* `dbo.Contact`
* `dbo.Organization`

Use current plural tables:

* `dbo.Cases`
* `dbo.Contacts`
* `dbo.Organizations`

---

# Recommended SQL Safety Rules for Codex

Before changing DAO SQL:

1. Consult `architecture/codex-prompt-rules.md`.
2. Consult this file for schema-sensitive work.
3. Inspect the existing DAO/query code.
4. Verify any column that is not already used by existing code.
5. Do not introduce guessed column names.
6. Prefer existing DTO/view model mappings.
7. Keep tenant filtering using `ShaleClientId`.
8. Preserve soft-delete filters such as `IsDeleted = 0` where applicable.
9. Use deterministic ordering for `TOP (1)` / latest row lookups.
10. For relationship fields, prefer `OUTER APPLY TOP (1)` when duplicate role rows are possible.
11. If a field is not verified, return blank/null rather than crashing the entire page.

---

# Useful Schema Verification Queries

## List columns for important tables

```sql
SELECT
    s.name AS SchemaName,
    t.name AS TableName,
    c.column_id AS ColumnId,
    c.name AS ColumnName,
    ty.name AS DataType,
    c.max_length AS MaxLength,
    c.precision AS Precision,
    c.scale AS Scale,
    c.is_nullable AS IsNullable
FROM sys.tables t
JOIN sys.schemas s
    ON s.schema_id = t.schema_id
JOIN sys.columns c
    ON c.object_id = t.object_id
JOIN sys.types ty
    ON ty.user_type_id = c.user_type_id
WHERE s.name = 'dbo'
  AND t.name IN (
      'Cases',
      'CaseUsers',
      'CaseContacts',
      'CaseUpdates',
      'Users',
      'Contacts',
      'Organizations',
      'Roles',
      'Statuses'
  )
ORDER BY
    t.name,
    c.column_id;
```

## List columns for dbo.Cases

```sql
SELECT
    c.column_id,
    c.name AS ColumnName,
    ty.name AS DataType,
    c.max_length,
    c.precision,
    c.scale,
    c.is_nullable
FROM sys.columns c
JOIN sys.types ty
    ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.Cases')
ORDER BY c.column_id;
```

## List possible case grid fields

```sql
SELECT
    c.column_id,
    c.name AS ColumnName,
    ty.name AS DataType,
    c.max_length,
    c.is_nullable
FROM sys.columns c
JOIN sys.types ty
    ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.Cases')
  AND (
      c.name LIKE '%Status%'
      OR c.name LIKE '%Description%'
      OR c.name LIKE '%Summary%'
      OR c.name LIKE '%Incident%'
      OR c.name LIKE '%Injury%'
      OR c.name LIKE '%Negligence%'
      OR c.name LIKE '%Limit%'
      OR c.name LIKE '%Tort%'
      OR c.name LIKE '%Notice%'
      OR c.name LIKE '%Discovery%'
      OR c.name LIKE '%Date%'
  )
ORDER BY
    c.column_id;
```

## List role seed data

```sql
SELECT
    Id,
    Name,
    Description,
    IsActive,
    ShaleClientId
FROM dbo.Roles
ORDER BY
    ShaleClientId,
    Id;
```

## List status seed data

```sql
SELECT *
FROM dbo.Statuses
ORDER BY
    ShaleClientId,
    Id;
```

## Sample cases with grid fields only

```sql
SELECT TOP (50)
    c.Id,
    c.Name,
    c.CallerDate,
    c.DateOfInjury,
    c.StatuteOfLimitations,
    c.TortNoticeDeadline,
    c.DiscoveryDeadline,
    c.Description,
    c.Summary,
    c.ShaleClientId
FROM dbo.Cases c
WHERE c.ShaleClientId = 7
ORDER BY
    c.UpdatedAt DESC,
    c.Id DESC;
```

## Sample cases that have date/deadline values

```sql
SELECT TOP (50)
    c.Id,
    c.Name,
    c.CallerDate,
    c.DateOfInjury,
    c.StatuteOfLimitations,
    c.TortNoticeDeadline,
    c.DiscoveryDeadline,
    c.Description,
    c.Summary,
    c.ShaleClientId
FROM dbo.Cases c
WHERE c.ShaleClientId = 7
  AND (
      c.StatuteOfLimitations IS NOT NULL
      OR c.TortNoticeDeadline IS NOT NULL
      OR c.DateOfInjury IS NOT NULL
      OR c.DiscoveryDeadline IS NOT NULL
  )
ORDER BY
    c.UpdatedAt DESC,
    c.Id DESC;
```

---

# Prompt Header for Future Codex Tasks

Use this at the top of database-related Codex prompts:

```text
First consult architecture/codex-prompt-rules.md.

For SQL/DAO work, follow architecture/database-schema.md and architecture/tenancy-and-rls.md.
Do not assume column names.
Reuse existing DAO mappings where possible.
If a needed field is not documented, verify it from the live schema or existing code before using it.
If a field cannot be verified, leave it blank/null rather than introducing a crashing SQL reference.
```

---

## dbo.LinkTypes

Link type lookup table for case/external links. This table follows the same global/default plus tenant override model used by modularized lookup tables such as `dbo.Statuses` and `dbo.PracticeAreas`.

| Column          | Type          | Notes                                      |
| --------------- | ------------- | ------------------------------------------ |
| `Id`            | int           | Primary key                                |
| `ShaleClientId` | int           | Tenant id, nullable for global/default row |
| `Name`          | nvarchar(100) | Display name                               |
| `Color`         | nvarchar(20)  | Display color                              |
| `IsActive`      | bit           | Active flag                                |
| `IsDeleted`     | bit           | Soft delete flag                           |
| `SystemKey`     | nvarchar(64)  | Normalized lowercase overlay key           |
| `CreatedByUserId` | int        | Creator user id, nullable for global seed rows |
| `UpdatedByUserId` | int        | Last updater user id, nullable for global seed rows |
| `CreatedAt`     | datetime2     | Created timestamp                          |
| `UpdatedAt`     | datetime2     | Updated timestamp                          |
| `RowVer`        | rowversion    | Row version                                |

Effective Link Type semantics:

* Global/default rows use `ShaleClientId IS NULL`.
* Tenant override rows use the same normalized `SystemKey` as a global row with the tenant's `ShaleClientId`.
* Tenant-created custom types are represented as tenant rows with their own `SystemKey`.
* Effective lists should read global rows plus current-tenant rows and choose the tenant row when a non-null `SystemKey` appears in both scopes.
* `UX_LinkTypes_ShaleClientId_SystemKey_NonNull` prevents duplicate non-null `SystemKey` values within the same scope.

Seeded global keys from `docs/sql/2026-07-16_case_links_foundation_phase1.sql`:

| SystemKey                | Name                   |
| ------------------------ | ---------------------- |
| `court_docket`           | Court Docket           |
| `claims_portal`          | Claims Portal          |
| `medical_records_portal` | Medical Records Portal |
| `insurance_portal`       | Insurance Portal       |
| `document_repository`    | Document Repository    |
| `government_record`      | Government Record      |
| `research`               | Research               |
| `other`                  | Other                  |

---

## dbo.ExternalLinks

Tenant-owned reusable hyperlink records.

| Column            | Type           | Notes                                                        |
| ----------------- | -------------- | ------------------------------------------------------------ |
| `Id`              | int            | Primary key                                                  |
| `ShaleClientId`   | int            | Tenant id                                                    |
| `LinkTypeId`      | int            | Reference to `dbo.LinkTypes.Id`                              |
| `DisplayName`     | nvarchar(255)  | User-facing link label                                       |
| `Url`             | nvarchar(2048) | Full web URL                                                 |
| `Description`     | nvarchar(max)  | Optional description                                         |
| `IsDeleted`       | bit            | Soft delete flag                                             |
| `DeletedAt`       | datetime2      | Deleted timestamp                                            |
| `DeletedByUserId` | int            | Deleted by user id                                           |
| `CreatedByUserId` | int            | Creator user id                                              |
| `UpdatedByUserId` | int            | Last updater user id                                         |
| `CreatedAt`       | datetime2      | Created timestamp                                            |
| `UpdatedAt`       | datetime2      | Updated timestamp                                            |
| `RowVer`          | rowversion     | Row version                                                  |

Tenant validation note: SQL Server cannot enforce the Link Type tenant boundary with a simple foreign key because `dbo.LinkTypes` intentionally allows global rows (`ShaleClientId IS NULL`) and tenant rows. Service code must ensure `ExternalLinks.LinkTypeId` references either a global Link Type or a Link Type owned by the same `ShaleClientId`.

---

## dbo.CaseLinks

Tenant-owned associations between cases and reusable external links.

| Column            | Type           | Notes                           |
| ----------------- | -------------- | ------------------------------- |
| `Id`              | int            | Primary key                     |
| `ShaleClientId`   | int            | Tenant id                       |
| `CaseId`          | int            | Reference to `dbo.Cases.Id`     |
| `ExternalLinkId`  | int            | Reference to `dbo.ExternalLinks.Id` |
| `IsPrimary`       | bit            | Primary case link flag          |
| `Notes`           | nvarchar(2000) | Case-specific notes             |
| `SortOrder`       | int            | Explicit user ordering          |
| `IsDeleted`       | bit            | Soft delete flag                |
| `DeletedAt`       | datetime2      | Deleted timestamp               |
| `DeletedByUserId` | int            | Deleted by user id              |
| `CreatedByUserId` | int            | Creator user id                 |
| `UpdatedByUserId` | int            | Last updater user id            |
| `CreatedAt`       | datetime2      | Created timestamp               |
| `UpdatedAt`       | datetime2      | Updated timestamp               |
| `RowVer`          | rowversion     | Row version                     |

Integrity protections:

* `UX_CaseLinks_CaseId_ExternalLinkId_Active` prevents the same active ExternalLink from being associated to the same case more than once.
* `UX_CaseLinks_CaseId_Primary_Active` allows at most one active primary link per case.
* Both filtered indexes ignore soft-deleted rows so legitimate replacements are not blocked.
* Service code must keep `CaseLinks.ShaleClientId` equal to the linked case and external link tenants; the legacy single-column primary keys on `dbo.Cases` and `dbo.ExternalLinks` prevent this tenant boundary from being enforced with a verified composite foreign key in phase 1.
