# Shale Database Schema Reference

_Last updated: 2026-06-15_

This document is intended to be the working schema reference for Codex and Shale development prompts.

**Important:** This file should be treated as a living reference. Before adding or changing SQL, verify column names against the live database or against the current DAO/query code. Do not introduce new column names based on memory or naming guesses.

---

## Development Tenant

Common development tenant:

| Item | Value |
|---|---|
| ShaleClientId | `7` |
| Tenant display name | `Curtis & Co.` |
| City | `Albuquerque` |
| State | `NM` |

---

## Tenancy / Row Level Security

Shale uses tenant isolation by `ShaleClientId`.

Runtime DB connections should stamp SQL Server `SESSION_CONTEXT` before tenant-scoped queries.

Relevant context keys:

| Key | Purpose |
|---|---|
| `ShaleClientId` | Tenant isolation key |
| `PrincipalEmail` | Optional principal/user context |

General rule:

```sql
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = @TenantId, @read_only = 1;
```

RLS predicate function previously used:

```sql
rls.fn_TenantMatch(@ShaleClientId INT)
```

RLS-covered tables include:

- `dbo.Cases`
- `dbo.Categories`
- `dbo.Contacts`
- `dbo.Organizations`
- `dbo.OrganizationType`
- `dbo.Priorities`
- `dbo.Roles`
- `dbo.Statuses`
- `dbo.Tasks`
- `dbo.Users`

---

## Core Tables

## dbo.ShaleClients

Tenant/client table.

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `DisplayName` | Tenant display name |
| `City` | City |
| `State` | State |
| `Postal` | Postal code |
| `IsActive` | Active flag |

---

## dbo.Users

Application users.

| Column | Notes |
|---|---|
| `id` | Primary key |
| `name_first` | First name |
| `name_last` | Last name |
| `email` | Email address |
| `email_norm` | Normalized email |
| `password_hash` | Password hash |
| `password_alg` | Usually `bcrypt` |
| `color` | User color |
| `is_attorney` | Attorney flag |
| `is_admin` | Admin flag |
| `is_deleted` | Soft delete flag |
| `default_organization` | Default organization |
| `organization_id` | Organization reference |
| `initials` | User initials |
| `created_at` | Created timestamp |
| `last_login` | Last login timestamp |
| `failed_attempts` | Login failure count |
| `lock_until` | Lockout timestamp |
| `ShaleClientId` | Tenant id |
| `LegacyUserId` | Legacy import id |

Common display expression:

```sql
LTRIM(RTRIM(CONCAT(u.name_first, ' ', u.name_last)))
```

---

## dbo.Cases

Primary case table.

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `Name` | Case name |
| `CaseNumber` | Case number |
| `PracticeAreaId` | Practice area id |
| `CasePracticeAreaId` | Case practice area id |
| `CaseStatusId` | FK/reference to case status |
| `CallerDate` | Intake/caller date |
| `CallerTime` | Intake/caller time |
| `AcceptedDate` | Accepted date |
| `ClosedDate` | Closed date |
| `DeniedDate` | Denied date |
| `ClientEstate` | Client estate flag |
| `OfficePrinterCode` | Office printer code |
| `FollowUpMeetWithClient` | Follow-up flag |
| `FollowUpNurseReview` | Follow-up flag |
| `DateOfInjury` | Date of incident/injury. Use this for current “Date of Incident” unless live schema says otherwise. |
| `StatuteOfLimitations` | SOL deadline |
| `TortNoticeDeadline` | Tort claims notice deadline |
| `DiscoveryDeadline` | Discovery deadline |
| `MedRecsInHand` | Medical records in hand flag |
| `IncidentDescription` | Main case description / incident description |
| `Summary` | Case summary |
| `CreatedAt` | Created timestamp |
| `UpdatedAt` | Updated timestamp |
| `IsDeleted` | Soft delete flag |
| `ShaleClientId` | Tenant id |

### Do not assume these exist

The following column names have appeared in discussion or older schema notes, but must not be used unless verified in the live database:

| Column | Warning |
|---|---|
| `IncidentOccurred` | Caused a live SQL error: `Invalid column name 'IncidentOccurred'`. Do not use without verification. |
| `Discovered` | Verify before use. |
| `IncidentDate` | Verify before use. |
| `IncidentOccurredDate` | Verify before use. |

### Case description field

For grid/report descriptions, prefer:

```sql
c.IncidentDescription
```

Fallback to `c.Summary` only if the UI explicitly wants summary.

### Date of incident field

Use:

```sql
c.DateOfInjury
```

Do not use `c.IncidentOccurred` unless the live schema confirms it exists.

---

## dbo.Statuses

Status lookup table.

Known use: join `dbo.Cases.CaseStatusId` to status table to display case status.

Exact column names should be verified from the live schema before writing new SQL.

Likely/expected fields:

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `Name` or `Description` | Status display text |
| `IsActive` | Active flag |
| `ShaleClientId` | Tenant id |

Example case status values used in UI/business language:

- Potential
- Prelitigation
- Accepted
- Closed
- Denied

**Prompt rule:** Before using a status display column, inspect existing `CaseDao` joins/mappings or query the schema. Do not guess between `Name`, `Description`, or other display columns.

---

## dbo.Roles

Role lookup table for case contacts/users.

Known seed values:

| Id | Name | Description |
|---:|---|---|
| `1` | `client` | Primary client/contact |
| `2` | `caller` | Initial caller |
| `3` | `judge` | Assigned judge |
| `4` | `responsible_attorney` | Responsible attorney |
| `5` | `prelitigation` | Intake/pre-litigation person |

Columns:

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `Name` | Role name |
| `Description` | Role description |
| `IsActive` | Active flag |
| `CreatedAt` | Created timestamp |
| `UpdatedAt` | Updated timestamp |
| `ShaleClientId` | Tenant id |

---

## dbo.CaseUsers

Associates users with cases.

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `CaseId` | FK/reference to `dbo.Cases.Id` |
| `UserId` | FK/reference to `dbo.Users.id` |
| `Role` | Role id from `dbo.Roles.Id` |
| `IsPrimary` | Primary flag |
| `Notes` | Notes |
| `CreatedAt` | Created timestamp |
| `UpdatedAt` | Updated timestamp |

Important role ids:

| Role | Meaning |
|---:|---|
| `4` | Responsible attorney |
| `5` | Intake / pre-litigation person |

Responsible attorney lookup pattern:

```sql
LEFT JOIN dbo.CaseUsers cu_ra
  ON cu_ra.CaseId = c.Id
 AND cu_ra.Role = 4
 AND cu_ra.IsPrimary = 1

LEFT JOIN dbo.Users ra
  ON ra.id = cu_ra.UserId
```

If more than one user can match, use a deterministic `OUTER APPLY TOP (1)` ordered by `IsPrimary DESC, UpdatedAt DESC, Id DESC`.

---

## dbo.CaseContacts

Associates contacts with cases.

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `CaseId` | FK/reference to `dbo.Cases.Id` |
| `ContactId` | FK/reference to `dbo.Contacts.Id` |
| `Role` | Role id from `dbo.Roles.Id` |
| `IsPrimary` | Primary flag |
| `Notes` | Notes |
| `CreatedAt` | Created timestamp |
| `UpdatedAt` | Updated timestamp |

Important role ids:

| Role | Meaning |
|---:|---|
| `1` | Client |
| `2` | Caller |
| `3` | Judge |

Opposing counsel role id is not confirmed in this reference. Verify role seed data before writing SQL that depends on it.

Client lookup pattern:

```sql
LEFT JOIN dbo.CaseContacts cc_client
  ON cc_client.CaseId = c.Id
 AND cc_client.Role = 1
 AND cc_client.IsPrimary = 1

LEFT JOIN dbo.Contacts client
  ON client.Id = cc_client.ContactId
```

---

## dbo.Contacts

Contacts table.

Only the primary key is confirmed in this reference.

| Column | Notes |
|---|---|
| `Id` | Primary key |

Common contact fields should be verified from the live schema or existing DAO mappings before use. Likely fields may include names, phone, email, organization, etc., but do not assume exact column names in new SQL.

---

## dbo.Organizations

Organizations table.

| Column | Notes |
|---|---|
| `Id` | Primary key |

Other columns should be verified from the live schema or existing DAO mappings before use.

---

## dbo.CaseUpdates

Case notes/updates.

| Column | Notes |
|---|---|
| `Id` | Primary key |
| `CaseId` | FK/reference to `dbo.Cases.Id` |
| `NoteText` | Update/comment text |
| `CreatedByUserId` | FK/reference to `dbo.Users.id` |
| `IsDeleted` | Soft delete flag |
| `CreatedAt` | Created timestamp |

Latest case update pattern:

```sql
OUTER APPLY (
    SELECT TOP (1)
           cu.NoteText,
           cu.CreatedAt,
           cu.CreatedByUserId
    FROM dbo.CaseUpdates cu
    WHERE cu.CaseId = c.Id
      AND ISNULL(cu.IsDeleted, 0) = 0
      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
    ORDER BY cu.CreatedAt DESC, cu.Id DESC
) latestUpdate
```

Created-by user pattern:

```sql
LEFT JOIN dbo.Users updateUser
  ON updateUser.id = latestUpdate.CreatedByUserId
```

---

## dbo.Tasks

Master task table.

Columns should be verified from the live schema or existing DAO mappings before writing new task SQL.

Known relationships:

- Tasks can relate to cases.
- Tasks use statuses, priorities, assignees, and due dates in the application.
- Existing service port: `TaskServicePort`.

---

## dbo.Categories

Lookup table.

Tenant-scoped with `ShaleClientId`.

Verify columns before writing SQL.

---

## dbo.Priorities

Lookup table.

Tenant-scoped with `ShaleClientId`.

Verify columns before writing SQL.

---

## dbo.OrganizationType

Lookup table.

Tenant-scoped with `ShaleClientId`.

Verify columns before writing SQL.

---

# Known Legacy Tables

The following older singular table names existed during migration but should not be used for new work unless intentionally working on migration code:

- `dbo.Case`
- `dbo.Contact`
- `dbo.Organization`

Use current plural tables:

- `dbo.Cases`
- `dbo.Contacts`
- `dbo.Organizations`

---

# Case Grid / Reports Reference

## General Case Grid default columns

The app’s general case grid should support these default columns:

| UI Column | Source |
|---|---|
| Case Name | `dbo.Cases.Name` |
| Client | `dbo.CaseContacts` Role `1` + `dbo.Contacts` display name |
| Intake Date / Caller Date | `dbo.Cases.CallerDate` |
| Case Status | `dbo.Cases.CaseStatusId` joined to status lookup |
| Opposing Counsel | Verify role/source before use |
| Latest Case Update | Latest non-deleted `dbo.CaseUpdates.NoteText` |
| Description | `dbo.Cases.IncidentDescription` |
| Date of Incident | `dbo.Cases.DateOfInjury` |
| Statute of Limitations | `dbo.Cases.StatuteOfLimitations` |
| Tort Claims Notice Deadline | `dbo.Cases.TortNoticeDeadline` |
| Responsible Attorney | `dbo.CaseUsers` Role `4` + `dbo.Users` |

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

Case status should be available as:

- Visible/selectable grid column
- Sort option
- Filter option

Do not confuse case status with latest update / workflow note.

The old spreadsheet’s manually typed “Status” values such as “Pending updated records order” should map to **Latest Case Update** or a future dedicated workflow-tracking field, not to `CaseStatusId`.

---

# Recommended SQL Safety Rules for Codex

Before changing DAO SQL:

1. Consult this file.
2. Inspect the existing DAO/query code.
3. Verify any column that is not already used by existing code.
4. Do not introduce guessed column names.
5. Prefer existing DTO/view model mappings.
6. Keep tenant filtering using `ShaleClientId`.
7. Preserve soft-delete filters such as `IsDeleted = 0` where applicable.
8. Use deterministic ordering for `TOP (1)` / latest row lookups.
9. For relationship fields, prefer `OUTER APPLY TOP (1)` when duplicate role rows are possible.
10. If a field is not verified, return blank/null rather than crashing the entire Cases page.

---

# Useful Schema Verification Queries

## List all tables and columns

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
ORDER BY
    s.name,
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

## List role seed data

```sql
SELECT
    Id,
    Name,
    Description,
    IsActive,
    ShaleClientId
FROM dbo.Roles
ORDER BY Id;
```

## List case status seed data

```sql
SELECT *
FROM dbo.Statuses
ORDER BY Id;
```

---

# Prompt Header for Future Codex Tasks

Use this at the top of database-related Codex prompts:

```text
Before changing SQL or DAO mappings, consult architecture/database-schema.md.
Do not assume column names.
Reuse existing DAO mappings where possible.
If a needed field is not documented, verify it from the live schema or existing code before using it.
If a field cannot be verified, leave it blank/null rather than introducing a crashing SQL reference.
```
