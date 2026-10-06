# Phone and email validation: implementation and rollout

Implementation follows the decisions approved after PR #1824. The original inventory in
[phone-email-validation-plan.md](phone-email-validation-plan.md) remains the historical baseline;
its implementation checklist links to the current entry points. No production data was accessed,
and neither migration nor verification SQL was executed during implementation.

## Shared behavior

The dependency-free `ContactValueValidator` contract is in core. `ContactValues` in data implements it
using pinned Google libphonenumber **9.0.40** and ICU4J **78.3**. Desktop, server, services and DAOs use
that implementation. It performs no network lookups. Review numbering metadata updates periodically
and run parser regressions before changing pins. Validation establishes numbering-plan structure,
not that a number is assigned, reachable or owned by the person.

The phone default is explicitly **US**, displayed in forms. International input uses a country
calling code. Familiar punctuation is accepted; input/paste is unrestricted. The parser checks
`isValidNumber`, rather than accepting every parseable number. Separate extensions contain 1–12
ASCII digits; leading zeros are retained. `x`, `ext`, `ext.`, `extension` and `;ext=` suffixes are
extracted for new/changed input. Matching inline/separate extensions are accepted, conflicts rejected.
An extension alone is rejected even when the phone is optional/unavailable. New/changed structured
phones store trimmed main display input, E.164 main number, and extension separately. Users' scalar
phone column stores trimmed display main plus ` ext. digits`; it has no canonical column.
New/changed User phone display is limited to the documented 100-character scalar capacity; long input
receives a safe field error, never truncation. Verify optional phone-column capacity at deployment. Display
previews and `tel:` links are computed independently; unchanged historical storage is not reformatted.

Emails accept a single ASCII dot-atom local part, plus tags, apostrophes, subdomains and international
domain names. ICU performs nontransitional UTS #46 domain conversion, including `ß`, for transport;
storage keeps trimmed spelling and casing. No TLD allowlist or DNS/MX/SMTP checks apply. Existing
comparison keys and `Users.email_norm` identity semantics remain compatible, including the existing
case-insensitive account lookup. Unicode and punycode domain spellings are not retroactively merged
as account identities. Quoted local parts, address literals and SMTPUTF8 Unicode local parts have an
accurate **unsupported format** error: end-to-end transport support has not been established. They
are not described as universally malformed. Lists, display-name wrappers and embedded controls or
header characters are rejected. Existing account authentication does not apply the new-address grammar.

| Input | Result / message |
| --- | --- |
| Required phone blank | “Enter a usable phone number.” Intake also offers Phone unavailable with a reason. |
| Optional phone/email blank | Accepted; no blank structured point inserted. |
| `(303) 555-0123`, `303.555.0123`, `+44 20 7946 0958` | Accepted; canonical main `+13035550123` or `+442079460958`. |
| `1-800-234-5678` | Accepted if valid numbering-plan structure; business/toll-free/fax are not restricted to mobiles. |
| `3035550123 x001`, separate `001` | Accepted, extension `001`. Same main with extension `002` may coexist. |
| Inline `001`, separate `002` | “The inline and separate extensions must match.” |
| Extension `abc`, `１２`, or 13 digits | “Enter an extension of 1–12 digits (0–9).” |
| Blank phone plus extension | “Enter a phone number before adding an extension.” |
| `0`, `0000000000`, `911`, `12345`, `call me`, two full numbers | “Enter a complete valid phone number. Use +country code for international numbers; US is the default.” |
| `  O'Neil+tag@Sub.Example.technology  ` | Accepted; surrounding whitespace removed, spelling/casing retained. |
| `person@bücher.example`, `person@straße.example` | Accepted; international domain converted only for transport. |
| `a..b@example.com`, `a@`, `a@example.com,b@example.com`, `Name <a@example.com>` | “Enter one email address, such as name@example.com.” |
| `a@example.com` with embedded CR/LF/header text | Rejected with the same safe structure error. |
| `"quoted"@example.com` | “Quoted email local parts are not supported. Use an unquoted address.” |
| `用户@example.com`, `a@[127.0.0.1]` | “This email format is not supported. Use an unquoted address with an ASCII local part and a domain name.” |

## Unknown information and history

Intake has independent Client Phone and separate Caller Phone unavailable controls and reasons:
**Unknown**, **Not provided**, **No phone**. Selecting unavailable still validates retained nonblank
input. Caller-is-client uses the client phone/extension/reason; inactive separate-caller fields do not
create a second contact. Both create and duplicate merge append CLIENT/CALLER observations to
`dbo.IntakePhoneAvailability` in the intake transaction, with tenant, Case, Contact, role, reason,
actor, timestamp and RowVer. A null reason represents supplied information. No observation is
backfilled, and no blank/placeholder phone row is created. Observations are intake provenance, not a
tenant-wide policy or a claim that an existing Contact no longer has other phones. There is no new
provenance-history viewer in this change; approved operators can review the role observations.
New users still require a valid login email. No other requirements were silently relaxed.

Persistence loads the tenant-owned authoritative baseline under the existing transaction/locking
contract and checks opening concurrency tokens. Client flags cannot exempt submitted data. New,
copied and changed main/extension/email values validate; unchanged invalid active/deleted values can
be retained, removed or restored without rewriting their spelling, canonical column or extension.
UI review warnings are nonblocking for unchanged values. Invalid communication actions are disabled.
Deliberately selecting an invalid value as primary requires correction; an existing invalid primary
can remain during unrelated edits and parent restoration.

A removed child can restore its unchanged invalid former primary status only when tenant-owned
entity audit history establishes its prior primary status. `ContactPointHistory` reads the latest
relevant persisted point event, bound to tenant, point ID, parent type and parent ID. RowVer still
applies. Missing history grants no primary exception: restore as nonprimary, or supply a valid
preferred point when the Organization category requires one. This avoids fabricating lost history.
Restoration still obeys kind, primary, duplicate, ordering and ownership rules. Contact categories
allow at most one primary; Organization emails require one when populated, and populated voice
phones require a preferred voice point. Removing a preferred Organization compatibility point
compacts remaining order and validates any newly promoted value. To remove multiple invalid points
without promoting one, use the complete editor in one transaction.

## API compatibility and ownership

Existing v1 routes remain available with their historical scalar update shape. New browser writes
use **PATCH `/api/v2/contacts/{id}`** and **PATCH `/api/v2/organizations/{id}`**. V2 has nested `details`
for the other fields and separate `phone`, `fax` (Organization), and `email` operation objects:

```json
{
  "expectedUpdatedAt": "2026-10-06T00:00:00Z",
  "details": {"firstName": "Updated"},
  "phone": {"action": "SET", "value": "3035550123", "extension": "001"},
  "email": {"action": "CLEAR"}
}
```

Omitted communication operation means **RETAIN**. SET supplies a value and optional extension;
CLEAR removes the optional owned point. RETAIN/CLEAR cannot contain values. SET is validated even
if the client tries to characterize it as unchanged; exemptions depend on the stored baseline.
Organization requests use opening Base64 `rowVer`; Contact requests use opening ISO
`expectedUpdatedAt` (a historical null timestamp is retained as a null opening token and checked
under the DAO lock; once set, a timestamp is mandatory). Detail reads include these tokens and the displayed phone/fax extensions.
Stale updates return a safe conflict; field failures return `validation_failed` and
`fieldErrors: [{field, code, message}]` through `ApiErrorResponse`, with no submitted values. Browser
forms retain state and focus the failing field. Advisory authenticated
`POST /api/validation/contact-value` uses the same parser for blur feedback, but never replaces the
persistence check. V1 callers must continue providing their historically required update fields;
omission semantics are changed only in v2. Migrate external callers deliberately.

Contact basic saves own the single active point shown by their preferred projection, chosen by
primary/order/ID. They preserve its kind, ID and history; they do not delete/reinsert unchanged
points or delete other points by category. Organization compatibility saves own their displayed
voice/fax/email point and preserve its kind. Complete editors retain their exact-set/owned-collection
contracts and child tokens. Basic Contact creates/updates and User basic/profile creation now append
non-PHI entity audits on the transaction Connection before commit; failures roll back. Existing
post-commit refresh/live publication boundaries remain in their existing callers. Audit/live
metadata contains structural IDs, kind and primary only, never phone/email input or parser output.

## Deployment

1. Review a database backup/recovery plan and the existing migration prerequisites: structured Contact
   and Organization contact points, user RowVer and identity uniqueness, entity audit, strict tenant
   function and enabled TenantFilter. Discover external producers and installed desktop versions.
2. In a disposable SQL Server environment, apply
   [migration](sql/2026-10-06_phone_email_validation.sql) using the approved administrative principal.
   Replace `@ExpectedDatabase`, independently establish all-tenant visibility and set its acknowledgement
   to 1. Tenant/actor SESSION_CONTEXT must be null for this administrative deployment. The script is
   guarded, transactional and forward-only; it neither backfills nor cleans contact information.
3. Run [verification SQL](sql/verification/2026-10-06_phone_email_validation_verification.sql). Review
   zero schema/capacity/predicate/index/ownership finding counts, trusted foreign keys, column types,
   actual index filter/columns, and function definitions. Repeat with two disposable tenants to verify
   read/write isolation and foreign-key rejection. Test caller/client observations, rollback on audit
   failure and opening-version conflicts. These database checks have **not been run here**.
4. The migration adds the availability table and tenant filter/write-block predicates, expands only
   undersized Organization Phone/Fax to `nvarchar(255)` and Email to `nvarchar(320)`, and replaces the
   Organization phone active-value index with one including Extension. Existing values are not
   truncated, and no broad phone/email syntax CHECK is introduced. Width changes require a maintenance
   window appropriate to the actual catalog/index dependencies; inspect those before deployment.
5. Deploy the data/parser dependencies with **all desktop clients**, server and browser together after
   the additive schema is present. Inventory and retire/upgrade older direct-JDBC clients and external
   writers before claiming enforcement. Server validation cannot prevent an older desktop DAO from
   writing `0` or rewriting structured rows. Existing deployment/version-control mechanisms must enforce
   that upgrade policy; this change does not add a new SQL syntax constraint or a client-version gate.
6. Run the acceptance checklist below; then perform a separately authorized review audit/cleanup.
   A code rollback can leave the additive schema in place. Do not rerun historical backfills, shrink
   mirrors, drop history, or automatically normalize old values as rollback steps.

## Read-only audit and cleanup

Use an approved tenant-scoped read-only connection and secured export location. Export ID, parent ID,
RowVer, active/deleted/primary/kind, display main, canonical column and extension from
`ContactPhoneNumbers`, `ContactEmailAddresses`, `OrganizationPhoneNumbers`,
`OrganizationEmailAddresses`; include optional Users phone and required Users email, Organization
compatibility mirrors, and intake availability observations. Verify actual optional Users phone-column
name from the catalog. Restrict the export to authorized tenants; establish explicit administrative
visibility separately for an all-tenant audit. Never bypass RLS by guessing a tenant context.

Run the pinned shared parser offline against display input and extension. Report aggregate counts by
field, reason code, active/deleted/primary, missing availability provenance, mirror mismatch and
canonical mismatch; supply restricted row IDs/tokens for review, not raw values in logs, live events,
PRs or audit metadata. Valid structure is not contactability. Preserve the export securely and delete
it under the organization's approved retention process. No audit query/export was executed here.

A reviewer can confirm a correction, remove an optional point, record unavailable intake information
in a new intake observation, or leave a follow-up item. Apply an explicitly reviewed correction through
normal versioned/aggregate saves with opening tokens and transaction audits. No cleanup script or
backfill is supplied. Historical invalid values and missing prior-primary history remain review items;
unrelated edits and supported restoration do not force cleanup.

## Windows and browser acceptance checklist (manual, not run)

- In light and dark mode, type/paste without filters. Blur/Save `0`, blank, valid US/international,
  fax/toll-free, leading-zero extensions, conflict and extension-only inputs; inspect readable messages,
  previews and first-field focus. Failed Save must preserve every draft field.
- Complete intake with both roles independently unavailable; require a reason, reject retained `0`,
  test caller-is-client copying, local draft restore and readable backup. Repeat duplicate merge into
  existing contacts with multiple points; inspect preferred flag/order, nullable binds and observations.
- Create/edit Contact and Organization through complete and basic browser paths. Keep invalid legacy
  active/deleted values during name/notes edits. Remove/restore unchanged history; test former-primary
  evidence and missing-history fallback. Reject copied invalid input and new invalid primary selection.
- Confirm invalid legacy Call/Email is disabled; valid punctuation, international numbers/extensions
  and IDN email use safe `tel:`/`mailto:` output. Read-only displays must not rewrite historical values.
- Test Add User, admin edit, User View pencil/whole-profile saves, email casing and unchanged old login
  identifier authentication. Reopen after a persistence failure to confirm the retained draft.
- Browser: inspect v2 omitted RETAIN, SET, CLEAR and changed extension requests; safe field errors and
  focus. Use two tabs for stale tokens. Confirm v1 compatibility with existing callers deliberately.
- Disposable database only: verify transaction rollback on invalid child/audit failure, tenant isolation,
  cross-tenant parent/actor FK rejection, primary uniqueness, contiguous order, historical rows and
  refresh/live delivery only after commit. Review the deployment verification SQL output.

## Automated validation record

The focused parser, intent, intake, authoritative legacy/concurrency, direct basic point ownership,
trusted primary history, adapter, URI and API/auth tests passed. The repository selector's affected
suite, default critical reactor, browser TypeScript and static FXML/CSS checks were run. See the PR
validation record for final counts/results after the last edits. `git diff --check` is required before
commit. These checks use local JVMs and recording/proxy JDBC tests; they establish no live SQL Server,
Windows visual or production-data verification.

The informational `-Pall-tests` run exposed historical failures. A clean worktree at merged base
`772b77c3ddb35546305669a379c0a35e48d3ba48` reproduces 12 of them: AdministrativeReadAuditMigrationContractTest,
ApplicationInstanceHeartbeatMigrationContractTest, ApplicationReleaseImportContractTest,
CaseDaoCasesGridQueryTest, CaseDateTypeLifecycleCutoverContractTest, CaseDatesFinalRuntimeCleanupContractTest,
CaseOverviewConfigurationContractTest, CaseSummaryReportsContractTest, ContactPhase2BAuditMigrationContractTest,
FormConfigurationFoundationTest, SessionInvalidationPhase8AContractTest and UserSessionMigrationContractTest.
The changed intake merge assertion was updated and passes. The historical suite stops at data;
its downstream modules were not verified by that profile. Do not describe the full suite as passing.

## Current source locations

These links identify the final implementation entry points; names remain durable navigation anchors.

| Entry point | Source |
| --- | --- |
| Shared contract: `interface ContactValueValidator` | [ContactValueValidator.java:4](../shale-core/src/main/java/com/shale/core/validation/ContactValueValidator.java#L4) |
| Phone and email parser: `Phone phone(` | [ContactValues.java:21](../shale-data/src/main/java/com/shale/data/validation/ContactValues.java#L21) |
| Unavailable intake controls: `validateContactFields(boolean` | [NewIntakeController.java:1517](../shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java#L1517) |
| Intake create: `NewIntakeCreateResult createIntake(` | [CaseDao.java:617](../shale-data/src/main/java/com/shale/data/dao/CaseDao.java#L617) |
| Intake merge: `NewIntakeCreateResult mergeIntake(` | [CaseDao.java:542](../shale-data/src/main/java/com/shale/data/dao/CaseDao.java#L542) |
| Intake availability persistence: `void recordPhoneAvailability(` | [CaseDao.java:1084](../shale-data/src/main/java/com/shale/data/dao/CaseDao.java#L1084) |
| Basic Contact create: `int createContact(` | [ContactDao.java:884](../shale-data/src/main/java/com/shale/data/dao/ContactDao.java#L884) |
| Basic Contact update: `boolean updateBasicProfile(` | [ContactDao.java:800](../shale-data/src/main/java/com/shale/data/dao/ContactDao.java#L800) |
| Basic Contact point ownership: `void replaceBasicStructuredPoint(` | [ContactDao.java:1010](../shale-data/src/main/java/com/shale/data/dao/ContactDao.java#L1010) |
| Complete Contact authoritative inventory: `Map<Long,PointState> inventory(` | [ContactMutationDao.java:117](../shale-data/src/main/java/com/shale/data/dao/ContactMutationDao.java#L117) |
| Complete Contact validation: `void validatePhones(` | [ContactMutationDao.java:129](../shale-data/src/main/java/com/shale/data/dao/ContactMutationDao.java#L129) |
| Organization authority: `OrganizationFields reconcile(` | [OrganizationStructuredContactMutationDao.java:19](../shale-data/src/main/java/com/shale/data/dao/OrganizationStructuredContactMutationDao.java#L19) |
| Organization authoritative value validation: `Row validated(` | [OrganizationStructuredContactMutationDao.java:96](../shale-data/src/main/java/com/shale/data/dao/OrganizationStructuredContactMutationDao.java#L96) |
| Trusted historical primary evidence: `boolean wasPrimary(` | [ContactPointHistory.java:9](../shale-data/src/main/java/com/shale/data/dao/ContactPointHistory.java#L9) |
| New account boundary: `UserDetailRow createUser(` | [UserDao.java:375](../shale-data/src/main/java/com/shale/data/dao/UserDao.java#L375) |
| User basic save: `boolean updateBasicProfile(` | [UserDao.java:350](../shale-data/src/main/java/com/shale/data/dao/UserDao.java#L350) |
| User admin save: `UserUpdateResult updateManagedUser(` | [UserDao.java:480](../shale-data/src/main/java/com/shale/data/dao/UserDao.java#L480) |
| Contact v2 PATCH: `ContactDetail updateContactV2(` | [ApiReadController.java:581](../shale-server/src/main/java/com/shale/server/controller/ApiReadController.java#L581) |
| Organization v2 PATCH: `OrganizationDetail updateOrganizationV2(` | [ApiReadController.java:693](../shale-server/src/main/java/com/shale/server/controller/ApiReadController.java#L693) |
| Structured API errors: `handleFieldValidation(` | [ApiExceptionHandler.java:31](../shale-server/src/main/java/com/shale/server/controller/ApiExceptionHandler.java#L31) |
| Browser field feedback: `function ContactValueInput(` | [App.tsx:8](../shale-web/src/App.tsx#L8) |
| Browser PATCH operations: `function contactValueUpdate(` | [api.ts:348](../shale-web/src/api.ts#L348) |
| Desktop blur feedback: `Label phone(` | [ContactFieldFeedback.java:14](../shale-ui/src/main/java/com/shale/ui/util/ContactFieldFeedback.java#L14) |
| Safe communication actions: `URI telephone(` | [ContactExternalActions.java:21](../shale-ui/src/main/java/com/shale/ui/util/ContactExternalActions.java#L21) |
