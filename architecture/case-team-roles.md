# Case Team roles

## Phase 2 authority and discovered legacy behavior

`dbo.CaseUsers` remains the membership record. Before Phase 2 it had an integer identity `Id`, `CaseId`, `UserId`, nullable-in-practice legacy `RoleId`, `IsPrimary`, notes, and created/updated timestamps; it had no tenant column, row version, or assignment lifecycle. Tenant safety was inherited through Case/User joins and its physical-delete behavior. The old model permitted multiple rows for the same case/user (one row per legacy role) because no case/user uniqueness guarantee was established. Phase 2 therefore does not consolidate, delete, or overwrite deployed rows during migration.

Responsible Attorney was represented by legacy role 4 plus `IsPrimary`, selected from active tenant attorneys in the Case Overview editor, and read by many Case, summary, calendar, task, notification, report, and export projections. The editor physically replaced legacy CaseUsers rows. Those mutations touched `Cases.UpdatedAt`; they did not have an actor-aware transaction seam for entity-action audit or dedicated Case Timeline events.

## Phase 2 schema

The review-only Phase 2 migration adds and backfills `CaseUsers.ShaleClientId`, adds `CaseUsers.RowVer`, and explicitly makes `RoleId` nullable. It registers strict tenant RLS on `CaseUsers`. `CaseTeamMemberRoles` is a strict tenant-owned, soft-removable assignment table with a bigint identity, tenant/case/membership/definition identities, actor/timestamp lifecycle fields, and RowVer. Composite foreign keys bind an assignment to the membership tenant and case and bind its definition to either scope key 0 (global) or its own tenant. A filtered unique index prevents duplicate active assignments; a case/role index supports case-role reads.

Membership is valid with no assignment rows. Multiple distinct active assignment rows may reference one membership, and ordinary roles may be shared by multiple memberships.

## Transition rule

`CaseTeamMemberRoles` is the future role authority. `CaseUsers.RoleId` remains a deprecated, nullable compatibility projection for the arrow-based editor and legacy readers. A new multi-role assignment only fills that projection when it is empty or already represents that same legacy role; it never chooses an arbitrary primary role. Responsible Attorney is the one exception because its singular compatibility behavior is explicit. Removing the assignment represented by `RoleId` clears `RoleId` without removing membership.

The legacy editor continues to display one legacy-compatible assignment. On save it snapshots assignments that are not represented by `CaseUsers.RoleId`, replaces its displayed legacy rows, recreates their authoritative assignments through the global `LegacyRoleId` bridge, and restores the snapshot for members retained by the editor. Thus a role change replaces only the displayed compatibility assignment; additional roles are not silently deleted. Removing a member remains explicit and removes all that member's assignments.

## Responsible Attorney

Behavior is keyed by protected `responsible_attorney`, never display name. The actor-aware assignment transaction soft-removes any other active Responsible Attorney assignment in the case, clears the former compatibility projection, assigns/restores the selected member, synchronizes legacy role 4, writes audit/timeline events, and commits as one unit. Removing the role preserves membership. Removing the member removes all assignments before physically removing CaseUsers.

The new authoritative service read resolves Responsible Attorney from assignment `SystemKey`. Legacy projections continue to function during transition because every Phase 2 Responsible Attorney mutation synchronizes role 4 atomically. Migrating every legacy role first ensures both sources agree at deployment.

## Audit and timeline

Definitions retain `CASE_TEAM_ROLE`. Memberships use `CASE_TEAM_MEMBER`; assignments use `CASE_TEAM_MEMBER_ROLE`. The latter two require the separate forward-only audit allowlist successor. Actor-aware service mutations append entity-action audit rows on the business connection before commit and emit Case Timeline events for member added/removed, role assigned/removed, and Responsible Attorney changed. Metadata contains only Case ID. The legacy editor's existing actor-less DAO seam is intentionally not used to fabricate audit identity; introducing its actor-aware service wiring and full multi-role UI is part of the editor redesign.

## Deployment

Apply, after review, in this order:

1. `docs/sql/2026-09-03_case_team_role_definitions_phase1.sql` (already deployed).
2. `docs/sql/2026-09-03_case_team_member_roles_phase2.sql`.
3. `docs/sql/2026-09-03_case_team_member_audit_allowlist_phase2.sql`.
4. Deploy the application.

The schema migration returns total memberships, memberships with legacy roles, migrated active assignments, roleless memberships, unmapped legacy roles, duplicate active assignments, and cross-tenant violations. Missing/ambiguous bridges and any tenant inconsistency abort before commit. The intentional `prelitigation` to `prelitigation_staff` bridge is revalidated.

## Deferred

The arrow editor is not redesigned and does not expose zero/multiple role editing. Legacy role-based projections outside the new aggregate read remain compatibility consumers for this phase. A later editor/read cutover will consume membership/assignment DTOs everywhere and then retire `CaseUsers.RoleId` only after production reconciliation proves no compatibility dependency remains.
