Shale is a multi-tenant law firm case management platform.

Primary modules:
- shale-core
- shale-data
- shale-ui
- shale-desktop
- shale-server

Current primary client:
- Curtis & Co.
- Tenant separation via ShaleClientId

Desktop application:
- JavaFX
- Java 21
- Maven multi-module

Database:
- Azure SQL Server

Primary entities:
- Cases
- Contacts
- Organizations
- Tasks
- Users
- Notifications

Navigation:
My Shale
Tasks
Cases
Contacts
Organizations
Team
Settings

## Calendar feed source-of-truth rules

The desktop Calendar feed is a unified read model, not a separate scheduling store. `dbo.CalendarEvents` contains persisted scheduled events that users can create and edit. Task due dates are projected directly from `dbo.Tasks.DueAt`, legacy case calendar dates are projected directly from verified `dbo.Cases` date fields, and authoritative Case Dates Phase 3A projects active `dbo.CaseDates` occurrences directly from their owning table. Projected task, legacy case-date, and authoritative `CaseDates` items are read-only in Calendar and should navigate back to their authoritative task or case record instead of opening the manual event editor. Authoritative `CaseDates` use the `CASE_DATE:<CaseDates.Id>` feed identity and `CASE_DATE` source discriminator, preserve stored local `StartsAt`/`EndsAt`/`AllDay` values with range-intersection filtering, map `CaseDateTypes.CalendarCategory = DEADLINE` to `CASE_DEADLINES` and other permitted case-date categories to `OTHER_CASE_DATES`, coexist with legacy fixed-date projections without deduplication or migration, and keep manual `CalendarEvents` separate.

Task due dates and case date fields must not be duplicated into `dbo.CalendarEvents`; `CalendarEvents` remains the source of truth only for real persisted scheduled events. By default, the feed hides cancelled persisted events and completed tasks while continuing to respect task and case soft-delete filtering.
