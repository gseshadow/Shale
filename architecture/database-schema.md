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


### Customizable lookup type standard

For future design and modernization of tenant/global overlay lookup definition tables, use [customizable-lookup-types.md](customizable-lookup-types.md). That standard distinguishes recommended future shape from this file's live-schema facts, including `Statuses` as the case-status definition table and `CaseStatuses` as transactional history.

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

## dbo.CaseLinkShares

Strict tenant-owned associations recording Contacts with whom a case-specific Case Link has been shared.

| Column            | Type            | Notes |
| ----------------- | --------------- | ----- |
| `Id`              | int             | Identity primary key |
| `ShaleClientId`   | int             | Tenant id; strict RLS ownership |
| `CaseLinkId`      | int             | Reference to `dbo.CaseLinks.Id`; stores the case-specific link, not the reusable ExternalLink |
| `ContactId`       | int             | Reference to `dbo.Contacts.Id`; first release supports Contact recipients only |
| `SharedAt`        | datetime2       | User-asserted date/time the link was shared; defaults to current UTC time but is distinct from `CreatedAt` |
| `Notes`           | nvarchar(1000)  | Optional sharing/access notes |
| `IsDeleted`       | bit             | Soft-delete/unshared marker |
| `DeletedAt`       | datetime2       | Date/time the association was removed |
| `DeletedByUserId` | int             | User who removed/unshared the association when known |
| `CreatedByUserId` | int             | User who recorded the share |
| `UpdatedByUserId` | int             | Last editing user |
| `CreatedAt`       | datetime2       | Database record creation timestamp; defaults to current UTC time |
| `UpdatedAt`       | datetime2       | Last database record update timestamp |
| `RowVer`          | rowversion      | Row version for optimistic concurrency |

Relationship semantics:

* `dbo.CaseLinkShares` is a many-to-many association between `dbo.CaseLinks` and `dbo.Contacts`: one Case Link may be shared with multiple Contacts, and one Contact may receive links from multiple cases.
* Sharing is tracked by `CaseLinkId`, not by `ExternalLinkId`, so reused external URLs retain the case context of the share.
* The table records Shale's knowledge that a link was shared. It does not verify permissions with Box, Clio, another external system, or a remote URL.
* Only Contact sharing is represented in this phase; organization, user, generic entity, preview, credential, token, and permission-verification fields are intentionally out of scope.

Integrity protections:

* `PK_CaseLinkShares` is the single-column primary key on `Id`.
* Single-column foreign keys reference `dbo.ShaleClients(Id)`, `dbo.CaseLinks(Id)`, `dbo.Contacts(Id)`, and `dbo.Users(Id)` for `CreatedByUserId`, `UpdatedByUserId`, and `DeletedByUserId`.
* No cascade delete behavior is used; Shale preserves sharing history through soft deletion.
* `UX_CaseLinkShares_CaseLinkId_ContactId_Active` enforces at most one active association for the same `(ShaleClientId, CaseLinkId, ContactId)` while allowing historical soft-deleted rows and later re-shares.
* `IX_CaseLinkShares_ShaleClientId_ContactId_Active` supports future Contact views answering which active links have been shared with a Contact and includes `CaseLinkId` and `SharedAt`.
* Loading active shares for a Case Link is supported by the filtered unique active index prefix `(ShaleClientId, CaseLinkId, ContactId)`; avoid redundant indexes unless a future query plan proves they are needed.

Tenant consistency and lifecycle requirements:

* `CaseLinkShares.ShaleClientId` must match the referenced Case Link, Contact, and runtime actor Users tenant. Phase 5.2 keeps single-column foreign keys because the migration does not verify existing composite unique keys on `dbo.CaseLinks(ShaleClientId, Id)` or `dbo.Contacts(ShaleClientId, Id)` and must not redesign base tables solely for composite foreign keys.
* Services must validate Case Link, Contact, and actor tenant compatibility before inserting or mutating shares; strict RLS remains defense in depth.
* Active rows use `IsDeleted = 0`, `DeletedAt IS NULL`, and `DeletedByUserId IS NULL`.
* Removed/unshared rows use `IsDeleted = 1`, with `DeletedAt` populated and `DeletedByUserId` populated when an actor is known. Soft-deleted rows must not be treated as currently shared.
* Phase 5.3 Case Link deletion must soft-delete active `CaseLinkShares` rows in the same transaction that soft-deletes the parent Case Link.
* Contact soft deletion must not cascade-delete shares. Historical reads should preserve records where appropriate, but Phase 5.3 services must reject new active shares to deleted or unavailable Contacts.

## Case Link Shares service lifecycle (Phase 5.3)

`dbo.CaseLinkShares` records that a tenant-owned `dbo.CaseLinks` row was shared with, or made available to, a tenant-owned `dbo.Contacts` row. Phase 5.3 uses the existing `CaseServicePort -> CaseServiceAdapter -> CaseDao -> dbo.CaseLinkShares` path; no separate service stack or UI-owned persistence path is introduced.

Service and DAO rules:

* `ShaleClientId`, actor user id, case id, case link id, contact id, share id, `SharedAt`, and expected `RowVer` values for update/remove operations are validated at the service boundary.
* Share notes are trimmed and limited to 500 Unicode characters, matching `dbo.CaseLinkShares.Notes nvarchar(1000-byte schema output / 500 UTF-16 characters)`.
* Reads and writes explicitly prove tenant ownership through `dbo.Cases`, active `dbo.CaseLinks`, tenant-compatible `dbo.ExternalLinks`, same-tenant active actor `dbo.Users`, and same-tenant `dbo.Contacts` predicates.
* `dbo.CaseLinkShares` has strict tenant RLS through `sec.fn_FilterByTenant(ShaleClientId)`, but services still apply explicit tenant predicates as defense in depth.
* Current live `dbo.Contacts` rows have `ShaleClientId` and `IsDeleted`, but Contacts are treated as having an RLS gap for this feature; every share contact read/validation therefore includes `ct.ShaleClientId = ?` or `ct.ShaleClientId = cls.ShaleClientId` explicitly. Phase 5.3 does not attach Contacts to RLS.
* Active duplicate semantics are enforced by `UX_CaseLinkShares_CaseLinkId_ContactId_Active`; SQL Server 2601/2627 violations from that index are translated to “This contact is already shared on this link.”
* Update and remove operations include active-state and expected `RowVer` predicates and report optimistic conflicts when no row is affected.
* Removing a share soft-deletes it by setting `IsDeleted`, `DeletedAt`, `DeletedByUserId`, `UpdatedAt`, and `UpdatedByUserId`; rows are not physically deleted.
* Deleting a Case Link soft-deletes active `dbo.CaseLinkShares` rows for that link in the same transaction before the link/external-link cleanup finishes. If either side fails, the transaction rolls back.
* Contact deletion does not cascade to `dbo.CaseLinkShares`. Existing share rows remain historical records and can still be displayed using best available contact display information with an unavailable marker; new active shares reject deleted, unavailable, or cross-tenant Contacts.
* `listCaseLinks` batch-loads shares for the returned Case Link set and groups them by `CaseLinkId` to avoid per-card N+1 queries. `getPrimaryCaseLink` uses a focused share load for the one primary link.
* Active shares are ordered deterministically by contact display name, contact id, then share id.
* Phase 5.3.1 adds aggregate create/update operations for the Case Link editor. `createCaseLinkWithShares` inserts the ExternalLink, CaseLink, staged CaseLinkShares, and primary-state changes on one connection and transaction. `updateCaseLinkWithShares` updates Link fields, inserts new shares, updates edited shares with expected share `RowVer`, soft-deletes unshared rows with expected share `RowVer`, and applies primary-state changes in one transaction. Any validation, duplicate-key, or optimistic-concurrency failure rolls back the complete aggregate save.
* Contact selector options for the Shared With editor are loaded through the same Case service boundary, sorted deterministically by display name and Contact id, exclude deleted/unavailable Contacts, and include an explicit `Contacts.ShaleClientId` predicate because Contacts currently do not have their own TenantFilter predicate.
* The UI stages share additions, edits, and unshares until the main Add/Edit Link dialog is saved. Cancel creates or changes no ExternalLink, CaseLink, or CaseLinkShares rows.

## Case Links production-hardening notes (Phase 5.5)

The supported persistence path for Case Link and Link Type desktop operations is unchanged: JavaFX controller -> `CaseServicePort` -> `CaseServiceAdapter` -> `CaseGateway`/DAO gateway -> `CaseDao` -> SQL Server. Production-supported service/gateway methods must have explicit adapter and DAO-gateway delegation; interface defaults must fail explicitly rather than returning plausible empty lists, empty optionals, null values, false/zero values, or no-op mutation success.

Operation classification:

* Ordinary workflow reads: effective Link Type listing, Case Link listing, primary Case Link lookup, active share listing, Case Contact options, tenant Contact options, and Contact reverse shared-link lookup.
* Admin-only reads/mutations: Link Type administration listing, tenant custom Link Type creation, tenant override customization, activation/deactivation, and reset/remove override.
* Aggregate transactional mutations: create Case Link with staged shares and update Case Link with share additions/detail updates/unshares.
* Standalone lower-level mutations: create/update/delete Case Link, set Primary, backend reorder, add/update/remove CaseLinkShare. These remain available for desktop internals and future API use but must not bypass tenant, row-version, and audit review.
* Deferred API/web/live-update scope: REST routes, React UI, live-update publication, reorder buttons, URL previews, external credential handling, and reverse Contact-to-Link editing.

Transaction expectations remain connection-scoped and DAO-owned. Aggregate create commits or rolls back ExternalLink insert, CaseLink insert, primary-state changes, CaseLinkShare inserts, and actor audit fields together. Aggregate update commits or rolls back ExternalLink update, CaseLink update, primary-state changes, share additions, share detail updates, unshares, and row-version checks together. Case Link deletion soft-deletes active shares in the same transaction as parent Case Link deletion and primary replacement.

Primary invariants are enforced by DAO transaction logic and the filtered unique active-primary index: first active Link becomes primary, at most one active primary exists per Case, setting primary clears the previous active primary, non-primary updates do not demote the primary unless explicitly requested, deleting the primary promotes the deterministic next active Link by established SortOrder/Id ordering, deleting the only Link leaves no primary, and soft-deleted Links cannot remain primary. When creating a new explicitly Primary Link, the DAO must clear any existing active Primary in the same transaction before inserting the replacement CaseLink with `IsPrimary = 1`; rollback restores the previous Primary and prevents orphan ExternalLinks or partial shares. If the filtered unique index still rejects a concurrent Primary replacement, services/UI must present it as a concurrent Primary Link change rather than exposing SQL Server table, index, or key details.

Audit compatibility status: Shale's current PHI audit helper writes field-level values for known PHI-bearing Case/Contact/Task fields. Case Link and Link Type mutations have tenant and actor IDs available, but safe audit entries for these entities require a broader audit contract that records action plus stable entity IDs without URL, description, note, Contact PII, or RowVer contents. No migration or ad-hoc timeline substitute is introduced in Phase 5.5; follow-up should add first-class non-PHI entity-action audit support or extend the existing audit schema/DAO safely.

---

## dbo.EntityActionAuditLog

Phase 6.1 adds a dedicated append-only entity-action audit table because existing `dbo.AuditLog` is PHI/field oriented (`EntryDate`, `UserId`, `ObjectTypeId`, `ObjectId`, `FieldName`, `FieldCode`, typed value columns) and cannot safely represent entity actions without fake fields or sensitive value payloads.

| Column | Type | Notes |
| --- | --- | --- |
| `Id` | bigint | Identity primary key |
| `ShaleClientId` | int | Required tenant owner; strict RLS |
| `ActorUserId` | int | Required `dbo.Users.id` actor |
| `EntityType` | varchar(64) | Allowlisted entity type such as `LINK_TYPE`, `CASE_LINK`, `CASE_LINK_SHARE` |
| `EntityId` | bigint | Stable changed entity id |
| `Action` | varchar(64) | Allowlisted action such as `CREATED`, `UPDATED`, `DELETED`, `PRIMARY_SET`, `REORDERED`, `OVERRIDE_CREATED`, `OVERRIDE_RESET`, `ADDED`, `REMOVED` |
| `OccurredAt` | datetime2(7) | Immutable UTC event timestamp, default `SYSUTCDATETIME()` |
| `ParentEntityType` | varchar(64) | Optional safe parent type |
| `ParentEntityId` | bigint | Optional safe parent id |
| `CorrelationId` | uniqueidentifier | Optional operation correlation id |
| `Source` | varchar(64) | Optional source such as desktop/API |
| `Metadata` | nvarchar(1000) | Optional strictly allowlisted ID/state metadata only |

Audit metadata may contain only stable IDs and non-sensitive state markers, including CaseId, CaseLinkId, CaseLinkShareId, ExternalLinkId, LinkTypeId, ContactId, previous/new Primary CaseLinkId, reordered link count, and activation state. It must not contain URLs, descriptions, link notes, share notes, Contact names/emails/phones, credentials, RowVer bytes, raw commands/DTOs, SQL, or exception text. Ordinary application paths insert only and must not update/delete audit history.

## Phase 6.2 unified Audit Log viewer

The desktop Audit Log viewer now supports three read-only modes on one screen: **All**, **PHI Audit**, and **Entity Activity**. PHI Audit rows retain the `dbo.AuditLog` field/value semantics; Entity Activity rows retain the `dbo.EntityActionAuditLog` action/entity semantics and are not projected into fake `FieldName`, old-value, or new-value changes.

All mode loads bounded PHI and entity-action first-page result sets, maps them to a typed presentation row, merges by occurrence timestamp descending, applies a deterministic source/id tie-breaker, and then applies the final visible row limit. This is a bounded viewer merge, not a schema-level `UNION`.

Entity activity viewer reads are tenant scoped and read-only: the DAO requires `SESSION_CONTEXT(N'ShaleClientId')`, verifies it matches the requested tenant, predicates `EntityActionAuditLog.ShaleClientId`, joins `dbo.Users` by actor id and matching tenant for safe display names without requiring active users, and sorts by `OccurredAt DESC, Id DESC`. Audit history remains append-only; no update/delete viewer operations are introduced.

The viewer remains admin-only through the existing Settings/SceneManager/controller checks. Ordinary users must not gain Entity Activity visibility if they cannot view PHI Audit rows.

Entity activity timestamps are stored as UTC `OccurredAt` values and converted through the same application-local Java time path used by the viewer presentation before display while retaining UTC instants for ordering.

Entity activity metadata rendering is allowlist based. Only stable ID/state keys such as case, case-link, share, external-link, link-type, contact, primary-link, reorder count, and active state are rendered. Unknown, malformed, nested, oversized, or sensitive metadata is ignored; raw Metadata JSON is never displayed. Prohibited content includes URLs, link titles/descriptions/notes, share notes, Contact names/emails/phones, RowVer, credentials, SQL, exception text, commands, and DTO payloads.

If a combined All-mode load partially fails, the viewer must not present incomplete history as complete; it should report the failed category, log the exception, and provide a retry/refresh path. Future API Source values may be shown only as subtle safe labels such as Desktop, API, or System.

### Request workflow lookup overlays (2026-07-21)

`dbo.RequestMethods` and `dbo.RequestStatuses` provide the Requests Settings tenant/global overlay model for Material Request creation. The authoritative `dbo.RequestMethods` shape after the Phase 1 database prerequisite is:

| Column | Type | Nullability / notes |
| --- | --- | --- |
| `Id` | `int IDENTITY(1,1)` | Not null; primary key |
| `ShaleClientId` | `int` | Nullable global/tenant overlay scope |
| `SystemKey` | `varchar(64)` | Nullable stable overlay key |
| `Name` | `nvarchar(120)` | Not null |
| `Color` | `nvarchar(20)` | Nullable presentation value; added by `2026-07-24_request_methods_color.sql` |
| `SortOrder` | `int` | Not null; default `0` |
| `IsActive` | `bit` | Not null; default `1` |
| `IsDeleted` | `bit` | Not null; default `0` |
| `CreatedAt` | `datetime2` | Not null; UTC default |
| `UpdatedAt` | `datetime2` | Nullable |
| `RowVer` | `rowversion` | Not null; database generated |

`Color` uses the standard optional `nvarchar(20)` presentation contract documented for customizable lookups and already established by the authoritative `MaterialTypes` schema. The Phase 1 migration stores built-in defaults but **the application does not read or mutate `RequestMethods.Color` yet**. `RequestStatuses` retains its independent color implementation. Global rows seed the approved request methods (`email`, `phone`, `fax`, `mail`, `portal`, `in_person`, `other`) and lifecycle statuses including the creation default `requested`. The legacy `dbo.MaterialRequests.RequestMethod` and `dbo.MaterialRequests.Status` text columns remain in place; no Request Method foreign key is introduced.

---

## Case Dates

### dbo.CaseDateTypes

Customizable overlay lookup for authoritative case-date meanings. Uses nullable `ShaleClientId` for global built-ins, stable lowercase `SystemKey`, display `Name`, `Description`, constrained `CalendarCategory`, `Color`, `SupportsTime`, active/deleted lifecycle fields, actor metadata, timestamps, and `RowVer`.

### dbo.CaseDates

Strict tenant-owned case occurrence table with `ShaleClientId`, `CaseId`, `CaseDateTypeId`, `StartsAt`, optional `EndsAt`, `AllDay`, notes, actor metadata, soft-deletion metadata, timestamps, and `RowVer`. Multiple occurrences of the same type may exist on one case.

Ownership: `CaseDates` owns legal/factual case dates. Following the completed Phase 3B backfill and Phase 3C validation, it is the target authoritative runtime representation for the nine migrated fixed-date meanings; their `Cases` columns are retained temporarily for rollback/history only. `CalendarEvents` owns manually created calendar events only. The unified calendar projects from authoritative sources instead of becoming the owner or duplicating domain dates. Unmigrated workflow/lifecycle dates remain separate unless deliberately reclassified. Runtime cutover and later column removal are distinct, independently gated phases.
