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

The desktop Calendar feed is a unified read model, not a separate scheduling store. `dbo.CalendarEvents` contains persisted scheduled events that users can create and edit. Task due dates are projected directly from `dbo.Tasks.DueAt`, and case calendar dates are projected directly from verified `dbo.Cases` date fields. Projected task and case-date items are read-only in Calendar and should navigate back to their authoritative task or case record instead of opening the manual event editor.

Task due dates and case date fields must not be duplicated into `dbo.CalendarEvents`; `CalendarEvents` remains the source of truth only for real persisted scheduled events. By default, the feed hides cancelled persisted events and completed tasks while continuing to respect task and case soft-delete filtering.
