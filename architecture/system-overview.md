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

The desktop Calendar feed is a unified read model, not a separate scheduling store. `dbo.CalendarEvents` contains persisted scheduled events that users can create and edit. Task due dates are projected directly from `dbo.Tasks.DueAt`; accepted, denied, and closed dates remain lifecycle projections from `dbo.Cases` because they are outside the Case Dates migration; and active authoritative occurrences are projected directly from `dbo.CaseDates`. The former fixed `dbo.Cases` projections for intake, injury, medical-negligence, discovery, deadline, fee-agreement, and non-engagement dates were removed at the Calendar cutover because they duplicated or became stale beside `CaseDates`.

Authoritative `CaseDates` use occurrence identity (`CASE_DATE:<CaseDates.Id>`) and the `CASE_DATE` source discriminator, preserve stored local `StartsAt`/`EndsAt`/`AllDay` values with range-intersection filtering, and use the stored type identity plus the existing effective tenant/global type presentation rules. Any permitted calendar category and arbitrary custom type can appear; items are never collapsed by label or date. Deleted occurrences are hidden, stored historical type presentation remains the fallback when no active effective overlay exists, and no absent occurrence is fabricated from a workflow flag or `dbo.Cases` fallback.

Calendar is read-only for Case Dates: projected occurrences navigate to the owning case Dates section and are never written or copied to `CalendarEvents`. Local navigation/reload behavior and PHI-free `CaseDates` LiveBus invalidations refresh the feed; tenant checks, client-instance suppression, event-id coalescing, and load generations prevent cross-tenant corruption, loops, and stale responses. LiveBus carries identifiers and change classification only, never date values, labels, names, notes, or concurrency tokens.

Task due dates and Case Dates must not be duplicated into `dbo.CalendarEvents`; `CalendarEvents` remains the source of truth only for real persisted scheduled events. By default, the feed hides cancelled persisted events and completed tasks while continuing to respect task and case soft-delete filtering. The lifecycle authority for accepted, denied, and closed dates remains intentionally unchanged in this slice pending a separately proven status-history authority.
