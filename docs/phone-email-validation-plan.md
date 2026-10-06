# Phone and email validation: inventory and implementation plan

Status: inspection and proposal only. No validators, application behavior, database objects, or data
were changed. Inspected the fetched `origin/codex/latest` at
`e5c6229fd94cc8a7275c9ba10e881442997013d5` on 2026-10-06. Source line references below are for that
revision; method names are the durable navigation anchors. Database statements below describe
repository schema/migration contracts, **not a live catalog or data verification**.

## Findings and scope

Required intake phones are checked only for nonblank text, so `0` satisfies the UI requirement.
No authoritative intake phone usability check follows it. Contact aggregate phones are similarly
nonblank/length checked. Email rules differ between desktop aggregate editors, basic-profile DAOs,
user administration, browser forms, and HTTP controllers. Normalization often strips characters or
lowercases text without establishing validity. A normalized value is not proof of usability.

The main boundaries that implementation must close are:

1. `CaseDao` intake create **and duplicate merge** write structured Contact points independently of
   `ContactMutationDao`.
2. Contact basic create/update (including public APIs) bypass aggregate validators and replace
   category-specific rows. A stronger desktop editor alone leaves these paths open.
3. Organization structured and scalar compatibility paths reach one transaction owner, but its
   child validator currently checks presence rather than phone/email syntax.
4. User administration checks only `contains("@")`; User View's basic save bypasses even that check.
5. Full Contact saves validate and rewrite every submitted child, including unchanged removed history.
   Invalid legacy email can block a Notes/name change. Organization desktop validation also checks
   every active email even during an unrelated edit.
6. Phone normalization, duplicate comparison, and external dialing disagree across domains.

Inventory discovery used phone/email/Fax matches in Java, FXML, TypeScript, POMs, SQL and docs, followed
by controller event handlers, commands, production adapter delegation, DAO SQL and tests. Read-only
projections, stale historical documentation, and unused controls were checked separately; matches were
not counted as independent editable fields. The supplied repository contains no reachable contact/user/
organization CSV or spreadsheet importer, multi-record contact bulk editor, or standalone Contact/
Organization duplicate/merge command. Intake duplicate resolution is real and is inventoried below.
External importers, integrations, older installed clients, triggers and stored procedures cannot be
exhaustively established from this checkout; producer/catalog discovery is a deployment prerequisite.

Reviewed instructions and context: [repository rules](../architecture/codex-prompt-rules.md),
[Contact architecture](../architecture/contact-management.md),
[Contact legacy retirement inventory](../architecture/contacts-phase-3b-legacy-reference-inventory.md),
[Organization architecture](architecture/organization-management.md),
[schema](../architecture/database-schema.md), [tenancy](../architecture/tenancy-and-rls.md),
[system boundaries](../architecture/system-overview.md),
[development rules](../architecture/development-rules.md),
[design system](../architecture/design-system.md),
[shared services](web-api-migration-step-2.md), [server wiring](web-api-step-3.md),
[API validation/error contract](web-api-azure-readiness.md#step-4d-api-contracts-for-the-react-web-app),
and [local test selection](testing/change-aware-test-selection.md).
Earlier architecture phases describe superseded scalar authority. Current Java and the later
Organization Phase 3F sections establish the paths recorded here. In particular, Contacts no longer
write their retired phone/email scalar columns, while Organization scalars remain synchronized mirrors.

## Exact source map

Paths in this table link to the actual source files. A line number identifies the entry point, and
named neighboring methods identify the relevant validation or persistence worker.

| Key | Source and entry points |
| --- | --- |
| I-UI | [NewIntakeController](../shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java#L688): caller/client copying; `resolveDuplicateAndSave` L926, `submitIntakeMutation` L943, request capture L1009, draft restore L1221, `validateRequiredFields` L1471, `showValidation` L1602. [new-intake.fxml](../shale-ui/src/main/resources/fxml/new-intake.fxml#L40): caller phone/email L40–43 and client phone/email L57–58. |
| I-DB | [CaseDao](../shale-data/src/main/java/com/shale/data/dao/CaseDao.java#L465): `mergeIntake`, `mergeRoleContact` L510, `insertMissingContactPoints` L522, `hasContactPoint` L527, `createIntake` L531, `insertCallerContactIfNeeded` around L900, `insertIntakeContactPoints` L973, `insertIntakeContactPoint` L989, normalizers L1029–1037. |
| C-UI | [ContactsController](../shale-ui/src/main/java/com/shale/ui/controller/ContactsController.java#L157): directory Add opens `ContactViewController.showCreateEditor`. [ContactViewController](../shale-ui/src/main/java/com/shale/ui/controller/ContactViewController.java#L398): structured display; `showProfileEditor` L428–473; Save L458–465; `PointEditor` L482–494, `PhoneEditor` L497, `EmailEditor` L499. |
| C-PORT | [ContactServicePort](../shale-core/src/main/java/com/shale/core/service/ContactServicePort.java): `CreateContactCommand`, `UpdateContactCommand`, complete profile commands, `IntendedPhoneNumber`, `IntendedEmailAddress`. [ContactServiceAdapter](../shale-data/src/main/java/com/shale/data/service/adapter/ContactServiceAdapter.java#L163): basic create/update L163–203 and aggregate delegation L217–228, production `DaoContactGateway` L292–328. |
| C-DB | [ContactDao](../shale-data/src/main/java/com/shale/data/dao/ContactDao.java#L759): `updateBasicProfile`, `createContact` L846, `replaceBasicStructuredPoints` L954, `replaceBasicStructuredPoint` L968; complete profile delegation L1682–1683. [ContactMutationDao](../shale-data/src/main/java/com/shale/data/dao/ContactMutationDao.java#L45): aggregate transaction, `mutateAggregate` L71, inventory L114, `validatePhones` L117, `validateEmails` L118, normalizer L120, `applyPhones` L121, `applyEmails` L122. |
| O-UI | [NewOrganizationController](../shale-ui/src/main/java/com/shale/ui/controller/NewOrganizationController.java#L34): shared editor loading/create. [OrganizationController](../shale-ui/src/main/java/com/shale/ui/controller/OrganizationController.java): `onEdit` L283 opens [EditOrganizationDialog](../shale-ui/src/main/java/com/shale/ui/controller/EditOrganizationDialog.java#L49); its `save` L53 validates and calls the aggregate; structured phone display/action L543. [OrganizationAggregateEditor](../shale-ui/src/main/java/com/shale/ui/controller/OrganizationAggregateEditor.java#L85): `validationError`, `contactMutation` L97, `PointEditor` L119, `PhoneEditor` L141, `EmailEditor` L143. |
| O-DB | [OrganizationServiceAdapter](../shale-data/src/main/java/com/shale/data/service/adapter/OrganizationServiceAdapter.java#L146): aggregates, scalar create L150 and patch adapter L152. [OrganizationDao](../shale-data/src/main/java/com/shale/data/dao/OrganizationDao.java): deprecated `create`/`update` adapters, connection-bound `createWithSingleTypeOnConnection`; `restoreOrganization` L597, `validateRestoreConsistency` L639. [OrganizationTypeMutationDao](../shale-data/src/main/java/com/shale/data/dao/OrganizationTypeMutationDao.java#L132): aggregate transaction owner; `createAggregateOnConnection` L144, `updateAggregate` L154, `validateFields` L199, mirror bindings L201–203. [OrganizationStructuredContactMutationDao](../shale-data/src/main/java/com/shale/data/dao/OrganizationStructuredContactMutationDao.java#L18): `reconcile`, `exact` L40, `legacy` L50, `update` L61, `validate` L64, normalizers L67. |
| U-UI | [UserManagementPane](../shale-ui/src/main/java/com/shale/ui/controller/UserManagementPane.java#L35): Add User, `validateAddUserEmail` L113, Edit L142–204, mutation feedback L281. [UserController](../shale-ui/src/main/java/com/shale/ui/controller/UserController.java#L988): whole-profile `onSave`, pencil dialog L1011, `saveSingleUserField` L1086, `saveUserProfile` L1100, `validateUserField` L2129, `UserField` whole-row requests L2180–2265. [user.fxml](../shale-ui/src/main/resources/fxml/user.fxml#L106): email/phone value, pencil and hidden editor controls. |
| U-DB | [UserDetailService](../shale-ui/src/main/java/com/shale/ui/services/UserDetailService.java#L75): basic-save delegation. [UserDao](../shale-data/src/main/java/com/shale/data/dao/UserDao.java#L333): `updateBasicProfile`, create L382, create validator L436, `normalizeEmail` L444, admin `updateUser` L477–503, reactivation L543, candidate phone-column discovery L817, duplicate lookup L898. |
| HTTP | [ApiReadController](../shale-server/src/main/java/com/shale/server/controller/ApiReadController.java#L492): Contact POST L494, PATCH L527; Organization POST L579, PATCH L619–641; Team GET L671–682. [ApiValidation](../shale-server/src/main/java/com/shale/server/controller/ApiValidation.java#L26): login; `optionalEmail` and `optionalContactText`. [ShaleServerServiceConfiguration](../shale-server/src/main/java/com/shale/server/config/ShaleServerServiceConfiguration.java): production port beans with request-scoped DB context. |
| WEB | [App.tsx](../shale-web/src/App.tsx#L2448): `ContactCreateForm`, `ContactDetailsForm` L2527, `OrganizationCreateForm` L2607, `OrganizationDetailsForm` L2785; `LoginPage` vicinity L505. [api.ts](../shale-web/src/api.ts#L732): Contact POST/PATCH L732–777, Organization POST L801 and PATCH L839; request types L150–235. |
| EMBED | [PartyAddWorkflowDialog](../shale-ui/src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java#L167): name/type inputs only. [CaseController](../shale-ui/src/main/java/com/shale/ui/controller/CaseController.java#L4213): `createEntityForNewPartyDraft`, client assignment creation L8924. [RequestedFromWorkflowDialog](../shale-ui/src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java): name/type creation selection. [CaseMaterialsTabController](../shale-ui/src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java#L136): `createRequestedFromEntity` supplies null phone/email. I-DB `persistPendingParty` creates name-only entities. |
| ACTION | [ContactExternalActions](../shale-ui/src/main/java/com/shale/ui/util/ContactExternalActions.java#L23): permissive one-argument telephone/email URI builders and stricter two-argument phone/extension builder. [OrganizationCard](../shale-ui/src/main/java/com/shale/ui/component/OrganizationCard.java#L90): optional validated action. [ContactCard](../shale-ui/src/main/java/com/shale/ui/component/ContactCard.java): display projection. |
| AUTH | [LoginController](../shale-ui/src/main/java/com/shale/ui/controller/LoginController.java#L246), [DesktopUiAuthService](../shale-desktop/src/main/java/com/shale/desktop/ui/DesktopUiAuthService.java), [AuthServiceImpl](../shale-data/src/main/java/com/shale/data/auth/AuthServiceImpl.java#L30): desktop credential lookup. [AuthController](../shale-server/src/main/java/com/shale/server/controller/AuthController.java), [DesktopSessionController](../shale-server/src/main/java/com/shale/server/controller/DesktopSessionController.java): server credential exchange through API validation/auth port. |

## Field inventory and persistence paths

“Optional collection” means an entity may have no phone/email rows. Once the user adds a row, its
value is required; an empty row is not an alternative representation of unavailable information.
All listed kinds share the same current rules within their path. Fax is a contact phone kind and
must eventually use the same parser, while remaining excluded from Call actions.

| Field/screen/workflow | Stored field and actual write path | Current requirement and validation | Normalization, display and feedback | Other paths and unchanged legacy behavior |
| --- | --- | --- | --- | --- |
| Intake **Client Phone Number** | I-UI → `CaseDao.createIntake` or `mergeIntake` → I-DB inserts `dbo.ContactPhoneNumbers.DisplayNumber`, `NormalizedNumber`, Kind `MOBILE`; client relationship is `CaseParties`, not a Case phone column. | Required by I-UI `validateRequiredFields`; only blank rejection. DAO point worker has no syntax or required-phone check; null skips insertion. | Request/point text trimmed; normalizer strips everything except digits/`+`, no region/length check; no input mask or phone display reformatting. UI validation label aggregates errors, then save failure handling. | Direct DAO request bypasses UI requirement. Merge uses the same raw writer and normalized-value duplicate lookup, not Contact aggregate validation. Existing rows are retained; incoming new points need validation even when merging. |
| Intake **Caller Phone Number** | Same path/table, separate caller Contact, Kind `MOBILE`; `callerIsClient=true` reuses client ID. | Required only when Caller is Client is unchecked, enforced by I-UI; no DAO phone usability check. | Same trim/strip/feedback as client. Toggle logic updates required marker and can copy caller values into empty client fields (I-UI L688–696). | Caller/client copying must preserve validation provenance. When caller is client, unused caller inputs must not impose independent required errors; when not, caller is independent. Merge reuses only an unambiguous role Contact. |
| Intake **Client Email** | I-DB → `dbo.ContactEmailAddresses.EmailAddress`, `NormalizedEmail`, Kind `PERSONAL`. | Optional; no UI/DAO address-format validation. Null/blank creates no row. | Trimmed value, lowercase normalized value; no mailbox check. Validation label currently has no email-specific error. | Bypasses Contact aggregate regex. Invalid nonblank email can persist, then fail later Contact aggregate saves. Merge retains old points and adds nonmatching incoming normalized email. |
| Intake **Caller Email** | Same table/kind on caller Contact; reuses client Contact when caller is client. | Optional, same absence of syntax check. | Same trim/lowercase/feedback. | Same bypass and subsequent aggregate-save risk. Do not require both email and phone simply because both controls exist. |
| Desktop **New/Edit Contact: phone Display Number** for `MOBILE`, `HOME`, `WORK`, `FAX`, `OTHER` | C-UI → complete profile port → C-PORT production gateway → `ContactDao.createContactProfile`/`updateContactProfile` → C-DB `ContactMutationDao` transaction → `ContactPhoneNumbers`. | Optional list; every submitted row, **including deleted rows**, requires nonblank trimmed number ≤255. DAO validates kinds, IDs/RowVers, exact loaded set, primary/order state and duplicate active normalized number. No usability parsing. | Stored display trimmed; normalized digits with leading `+` retained only if display starts with it. Display uses entered punctuation; profile appends `ext.`; input is not masked. Errors stay in aggregate dialog status; rows are staged before outer Save. | Basic/API/intake writers bypass this validator. `0` is accepted; letters can normalize to empty digits. All children are rewritten/re-normalized during an unrelated aggregate edit. Unchanged phone that fails length/presence can block it. |
| Desktop **Contact phone Extension** | Same row, `ContactPhoneNumbers.Extension`, separately staged alongside Display Number. | Optional; DAO only trimmed length ≤20. UI has no digit rule. | Trim-to-null via DAO setter; shown with `ext.`. Contact profile Call uses **display number only**, so separate extension is not passed by this path. | Inline extension text in Display Number gets folded into normalized digits, unlike separate Extension. No shared collision/conflict rule. Existing extensions are resubmitted on unrelated edits. |
| Desktop **New/Edit Contact: Email Address** for `PERSONAL`, `WORK`, `OTHER` | Same aggregate path → `ContactEmailAddresses.EmailAddress`, `NormalizedEmail`. | Optional list; every submitted row including removed history requires nonblank ≤320 and `^[^\s@]+@[^\s@]+\.[^\s@]+$`. DAO validates after trimming; UI regex is on untrimmed text. Case-insensitive active duplicate check. | Stored display trimmed and casing retained; normalized lowercase. Profile/cards show entered address; `mailto` builds a URI, not an address validator. Dialog status retains entered data on validation failure. | Basic/API/intake bypass regex. Unchanged invalid **active or removed** email blocks unrelated aggregate edits. Surrounding whitespace can fail desktop before DAO could trim it; simplistic regex accepts some invalid dot/domain structures and rejects some legitimate quoted forms. |
| Browser **New/Edit Contact phone/email**; HTTP Contact POST/PATCH | WEB → api.ts → HTTP → `ContactServiceAdapter.createContact`/`updateContact` → C-DB **basic** methods → `ContactPhoneNumbers` `MOBILE` / `ContactEmailAddresses` `PERSONAL`. | Both optional. Browser `type=email` plus JS `includes('@')`, max254; phone `type=tel`, max100 without semantic validation. HTTP trims/nulls blanks, email ≤254 and `contains('@')`; phone ≤100. DAO basic methods have no syntax validator. | Basic DAO trim/strip phone and lowercase email normalization. Scalar detail is preferred active row ordered primary/sort/ID. Browser error state uses generic api.ts 400 messages (“Check the contact details and try again.”). | Raw HTTP clients bypass browser validation; direct port/DAO bypass HTTP validation. Contact PATCH is a full supported-field update: absent/null phone/email become null, not retain semantics. Basic save soft-removes all active `PERSONAL` emails / `MOBILE` phones and inserts replacements, even if unchanged; other kinds survive. Unrelated web edit can reject a preloaded bad email in browser or erase/recreate points. Preserve provenance and resolve primary collisions before adding rules. |
| Desktop **New/Edit Organization phone Display Number**, including Fax kind | O-UI → `OrganizationServicePort` aggregate → adapter → `OrganizationDao` → O-DB `OrganizationTypeMutationDao` → connection-bound reconciler → `dbo.OrganizationPhoneNumbers.DisplayNumber`, `NormalizedNumber`; owner synchronizes `dbo.Organizations.Phone` (preferred voice), `Fax` (first active ordered Fax). | Optional list. UI checks active rows nonblank ≤255, duplicate display+extension and supported kind; no parsing. Reconciler validates all submitted rows for nonblank, supported kind, ordering/primary/concurrency. Compatibility owner limits Phone/Fax to60. | Display retained, normalized only if stripping to digits/`+` produces optional `+` and 7–15 digits, otherwise null. Display appends extension. O-UI validation label/status keeps editor; persistence errors use safe failure handling. | Direct structured port/DAO bypasses UI length and duplicate checks (SQL widths/indexes still apply). `0` can be stored with null normalization. Unchanged active phone passes weak usability rules but mirror-length/consistency failures can block unrelated save; exact reconciliation can derive changed normalized values from old display text. |
| Desktop **Organization Extension** | `OrganizationPhoneNumbers.Extension`; mirror scalar is display text, without separate extension. | Optional; DAO length ≤20, no digit check. UI field optional. | Trim/null in bindings. Organization Call uses normalized number and separate extension; external builder accepts digits only, 1–12. Card omits unsafe action; profile action errors are localized. | Persistence accepts extensions the launch builder rejects. In-display extensions may be concatenated into main normalized digits. Unchanged extension is resubmitted with exact sets. |
| Desktop **New/Edit Organization Email Address**, `PERSONAL`, `WORK`, `OTHER` | O-UI aggregates → `OrganizationEmailAddresses.EmailAddress`, `NormalizedEmail`; owner derives `Organizations.Email` from preferred active email. | Optional list. Active UI entries ≤320 and same simplistic email regex; case-insensitive duplicates. Reconciler checks nonblank, **no regex**, even on submitted removed rows; mirror Email limit254. | UI uses trimmed `safe` text for validation; stored address casing preserved; normalizer lowercases only if `@` follows first character and no literal space, otherwise null. `mailto` URI builder checks only blank/control/URI safety. | Direct structured/scalar paths bypass desktop regex. Bad active email blocks unrelated desktop save; invalid removed syntax alone does not. Unknown historical kind must be changed before active UI exact save. Mirror limits can reject a UI-accepted long preferred address. |
| Browser/API **Organization Phone, Fax, Email**; legacy service/DAO adapters | WEB → HTTP → O-DB scalar adapter. POST `structuredCreateFromLegacy` maps Phone=`WORK`, Fax=`FAX`, Email=`WORK`; PATCH `LegacyContactMutation` patches safely owned compatibility row, preserving extra rows; reconciler and aggregate owner write structured values/mirrors atomically. | All optional. Browser email `type=email`, `includes('@')`, ≤254; phone/fax `type=tel`, ≤100. HTTP email contains `@`, ≤254; phone/fax text ≤100; aggregate mirror phone/fax ≤60. Child DAO lacks email/phone syntax. | Trim/null; normalization as O-DB above. Browser/API responses retain scalar projection. Generic browser 400 feedback; server standard safe error body. | Direct legacy service/DAO bypass HTTP/browser checks but still reaches aggregate owner. Organization PATCH treats missing/null scalar as retain and explicit blank as clear; unlike Contact PATCH. Omitted old email bypasses controller validation, but browser full form validates preloaded email. Legacy reconciler compares display text and can preserve unchanged backfilled normalized-null row. Ambiguous ownership/RowVer/primary consistency is a separate blocking conflict, not syntax cleanup. |
| Settings → Administration → Users **Add Email / login** | U-UI → `UserDao.createUser` (no separate User port create method) → `dbo.Users.email`; computed `email_norm` is not directly bound. | Required UI and DAO. Only `contains('@')` plus blank check. DAO rechecks tenant duplicate, including inactive accounts, with safe active/inactive duplicate messages. | Stored trim/lowercase (`Locale.ROOT`); database normalized lookup; inline email label on blur/save, management status on failure. | Basic User View save bypasses create validation. No Add User phone field: create binds discovered phone column as NULL. Invalid `@` can pass application syntax checks. User account email is both contact data and login identity; changing normalization/requiredness needs auth regression coverage. |
| Settings **Edit User Email / login and Phone** | U-UI → `UserDao.updateUser` → `Users.email` and discovered phone column. | Email required (`contains('@')`); phone optional and unvalidated. DAO enforces admin/tenant, duplicate check and RowVer. UI marks name/email invalid; no phone error. | Email trim/lowercase; phone blank-to-null/trim, no canonical parsing/formatting. `USER/UPDATED` event contains IDs/roles, not values. | Invalid legacy email without `@` blocks even role/color/name edit; `@` alone passes. User View basic save bypasses admin editor rules, uniqueness precheck and RowVer path. Update does not grandfather unchanged legacy syntax. |
| Team/User View **Email and Phone pencil dialogs / whole-profile save controls** | U-UI → `UserDetailService.updateBasicProfile` → `UserDao.updateBasicProfile` → same Users columns. | Email pencil permits blank; nonblank must contain `@` not at start/end; phone no check. Whole-profile `onSave` constructs request without this pencil check. DAO validates IDs/tenant only, no requiredness/syntax/duplicate precheck. | Basic setters trim/null values; email casing is not normalized by create/admin helper. Display raw value or em dash; dialog validation and generic “Failed to save user profile.” status. | Pencil name/color save includes unchanged email/phone in whole-row request; no semantic validation occurs. New bad values/blank login email can reach DB (subject to actual constraints). Old values can be rebound/trimmed without explicit repair. Hidden FXML controls are not proof of a currently visible edit mode, but `onSave` and DAO remain callable paths to cover. |
| Desktop/web **Sign in email**, desktop server-session credential exchange | AUTH / WEB login → authentication port/data auth lookup; HTTP `requireValidLogin` in login/session controller. Reads existing Users; does not create/update phone/email rows. | Desktop trims email and auth rejects blank; JDBC lookup verifies credentials. Web `type=email` required. HTTP requires blank/length≤254/`contains('@')`. | Desktop trims; auth query binds trimmed email. Safe invalid-credential feedback should not reveal account existence. Remembered sign-in is protected credential handling, not contact storage. | A new stricter contact validator must **not lock out existing accounts** by rejecting their legacy login identifier before authentication. Admin cleanup of login identity requires separate user-visible recovery process. Login is read/auth only, not a phone/email write bypass. |

### Other applicable paths and technical fields

- **Embedded Case party, Change Client, intake additional parties, Material Request Requested From:**
  EMBED supplies only names/type/identity. Contact create calls basic DAO with null phone/email;
  Organization creates delegate to the same aggregate with empty owned collections. These are legitimate
  entities without known contact points, not hidden required phone fields. Requested-from choices, case
  links/shared links, case parties, reports/documents, directories, search, user cards and administrator
  session/instance pages project existing values; they have no independent phone/email editors.
- **Dormant scalar Create Contact dialog:** [CreateContactDialog](../shale-ui/src/main/java/com/shale/ui/component/dialog/CreateContactDialog.java#L29) has optional Email and Phone text inputs, trims/blanks-to-null in `onCreate` L136–155, and validates only names with an error label. `showAndWait` returns a basic DAO request. Repository reference tracing found no construction/call site (CaseController has an unused import); the directory uses C-UI instead. It is not a reachable extra screen, but any future reuse must use shared validation and actor-aware persistence.
- **Contact child restore/remove/primary/reorder:** C-UI PointEditor stages all children, and C-DB exact
  aggregate validates all of them. Contact `PointEditor.remove` toggles deletion even for unsaved rows,
  while `applyPhones`/`applyEmails` new-row inserts do not bind submitted deletion state. Cover
  add-then-remove in implementation; it must discard the new draft rather than accidentally persist it.
  There is no separate
  parent Contact restore service/UI found; classification-definition/assignment Restore is not Contact
  restoration and does not edit phone/email values.
- **Organization child restore/remove/primary/reorder:** O-UI stages exact owned lists; O-DB validates
  submitted historical rows too, with row identity/concurrency checks. Independently removed children
  remain history during parent restore. Parent restore from removed directory calls
  `OrganizationServiceAdapter.restoreOrganization` → `OrganizationDao.restoreOrganization`: checks
  tenant/admin/RowVer, mirror equality, retained type/primary/order/ownership consistency, then updates
  **only the parent** and writes `ORGANIZATION/RESTORED`. It does not parse retained phone/email syntax.
- **User reactivate/remove:** `UserManagementPane` → `UserDao.reactivateUser` L543 only clears
  `is_deleted` for nonremoved same-tenant account; no email/phone syntax check. Password reset/lifecycle
  do not edit contact values. Removed users cannot be reactivated by this action.
- **Duplicate/merge:** I-UI “Merge Into Existing Case” and “Create Separate Case” both use the intake
  request. I-DB merge retains existing points and inserts incoming nonmatching normalized values.
  It must validate incoming values regardless of duplicate choice; do not rewrite old points.
  Its point insert hardcodes primary/order `1,0`, so adding to a populated category can collide with
  the one-primary index or ordering invariants. This is a related call-path gap for implementation tests,
  not a tested database failure in this inspection.
- **Imports/backfills and bulk editing:** no interactive contact-data importer/bulk field editor is
  exposed by inspected UI/service/API routes. Historical Phase 2C-A Contact and Phase 3A Organization
  migrations copy legacy values conservatively, including invalid history. They are separate producers,
  not validation templates; do not rerun backfills or retrofit syntax checks into immutable migration
  history. Release catalog import is unrelated. A future importer must call the same authoritative
  validator and report row/field errors; future multi-record operations must use per-record baselines.
- **Technical configuration:** no SMTP host, port, username, sender-address settings screen or SMTP
  client dependency was found in current Settings/FXML/config sources or dependency declarations.
  Settings routes user administration (above), not a separate tenant contact-email form. Request Method
  names `email`, `phone`, `fax` are classification keys, not addresses/numbers. `PrincipalEmail`, auth
  DTO email, token claims and protected remembered credentials are identity/context projections, not
  independently editable contact records. Do not apply contact regexes to usernames, SMTP hosts/ports,
  passwords, API origins, JDBC URLs or bearer claims. If a later SMTP feature has a sender mailbox,
  validate that mailbox with an explicitly named mail-configuration contract; SMTP username may be a
  non-email token. Absence in this checkout does not establish absence in external deployment tools.

## Persistence constraints and normalization gaps

The repository contract for both structured phone tables is `DisplayNumber nvarchar(255) NOT NULL`,
`NormalizedNumber nvarchar(32) NULL`, `Extension nvarchar(20) NULL`; email tables use
`EmailAddress nvarchar(320) NOT NULL`, `NormalizedEmail nvarchar(320) NULL`.
See [Contact point migration](sql/2026-08-26_contacts_phase2c_a_contact_points.sql#L37) and
[Organization point migration](sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql#L53).
They enforce kind, lifecycle, nonnegative order, tenant foreign keys/RLS, and at most one active primary.
Contact value CHECKs use `LEN(value)>0`; Organization uses trimmed nonblank CHECKs. Neither validates
usable phone or email structure. There is no entity-level requirement to have a phone/email child.
Organization active-value indexes cover `(tenant,parent,Kind,DisplayNumber)` for phones and
`(tenant,parent,EmailAddress)` for emails, not canonical phone+extension uniqueness. Contact active
normalized duplicate checks are in application code, not an equivalent value unique index.

Legacy `Contacts.PhoneCell`, `PhoneHome`, `PhoneWork`, `EmailPersonal`, `EmailWork`, `EmailOther`
remain historical schema/backfill inputs. No current Java save in this base writes them or uses them
as a fallback for current contact points. The older Phase 3B inventory mentions CaseContacts snapshot
phone/email insert names, but those names are absent from the current CaseDao: inspected CaseContacts
link/primary inserts at L3638/L4742 write relationship metadata only. Do not recreate stale projections
or audit retired scalar equality as a correctness gate.

Organizations.Phone/Fax/Email are mirrors maintained by the aggregate owner. Mirror limits in Java
are 60/60/254, smaller than child display capacities and some HTTP/UI limits. The schema document lists
Organization Email 508 and Users email 510 character types, while migration guards use SQL Server
`sys.columns.max_length` **bytes** (for example Organization Email 508 bytes =254 Unicode characters).
A read-only catalog preflight must establish effective lengths, collation, nullability and Users computed
`email_norm` expression before implementation; do not infer live widths from the historical prose alone.
User phone-column discovery currently chooses the first present candidate among `Phone`, `PhoneCell`,
`phone_cell`, `PhoneNumber`, `phone`, `phone_number`; if absent, reads yield null and saves omit it.
No phone-column choice or absence was confirmed against a database.

[Users email uniqueness migration](sql/2026-06-16_users_email_norm_unique.sql) supplies a filtered tenant
normalized-email unique index across lifecycle states. It guarantees uniqueness, not validity. Actual
presence/trust of checks/indexes/triggers and nullability remains unverified. Broad new database syntax
CHECKs would retroactively affect legacy rows and metadata-only saves; do not introduce them as the first
validation stage. Keep tenant/ownership/concurrency and structural constraints independent of syntax rules.

Current normalizers are incompatible:

| Producer | Phone result | Email result |
| --- | --- | --- |
| Contact aggregate | Strip every nondigit; retain leading `+` only when input starts with it. Even `0`/empty digit result is accepted; inline extensions become main-number digits. | Trim and lowercase after simple regex. |
| Intake / Contact basic | Strip everything except digits and `+`; no count/placement/usability check. | Trim/lowercase without syntax check. |
| Organization reconciler | Strip except digits/`+`; populate only optional-plus 7–15 digit result, otherwise NULL. | Lowercase only `@` after position 0 and no literal space; not an acceptance rule. |
| Historical Contact backfill | Normalize only already canonical `+` and 7–15 digits; preserve other display values with NULL normalization. | Conservative single-`@`/space/minimal shape check; preserve invalid original. |
| User administration vs User View | No canonical phone parsing. | Administration lowercases/trims; basic profile only trims/nulls. |
| Search / external actions | Search digit cleanup is for matching, not validation. One-argument Contact dialing only builds a URI; Organization two-argument builder requires optional-plus digits/`*#` ≥3, extension 1–12 digits. | `mailto` URI construction rejects controls/blank, not address grammar. |

## Proposed shared contract

### Validation and ownership

Use one JavaFX-free contract with structured results: field path/row ID, stable code (required,
invalid phone/email, invalid extension, too long, legacy warning), safe message, and parsed candidate.
Put contracts and field-policy descriptors in `shale-core`; implement the parser in shared non-UI code.
Desktop can call shared Java validation for early feedback; every authoritative mutation worker must
also call it so direct services, DAOs, HTTP and connection-bound intake cannot bypass it. React may
provide preview feedback but the server result is authoritative; use shared fixture cases to prevent
divergent client regexes. Do not trust a client-supplied `unchanged` or `alreadyValidated` flag.

- Required field: supply a usable value. Whitespace is absent; `0` is not presence for this purpose.
- Optional field: blank/null is allowed; a nonblank newly supplied or changed value must validate.
- Optional collection: no row means absence. An added row requires a usable value. Empty add drafts
  never enter the aggregate. Removal is an operation on identity/history, not a blank-value update.
- Requiredness is **workflow/field-specific**: retain the current intake client/caller condition and
  required new-user login email until product explicitly approves unavailable-information handling.
  Do not require phone on every Contact just because intake does.
- Reject embedded control characters, newlines and multiple values in a single value field; do not
  sanitize dangerous text into an apparently valid value. Keep original text in the editor after failure.
- Enforce actual storage/projection limits without truncation; return field-specific errors. Resolve
  preferred Organization mirror capacity before promising a 320-character preferred email.

### Phone parsing recommendation and dependency decision

No declared libphonenumber, Commons Validator, Jakarta/Javax Mail or equivalent phone/address library
was found in parent/module POMs; current helpers are handwritten. Spring validation/web dependencies
and HTML `type=tel` are not international phone parsers. This is a declaration/source inspection,
not an executed Maven dependency tree. `shale-core/pom.xml` explicitly prefers production dependency-free
core; do not add a parser there without reviewing that boundary.

Recommend pinned **Google libphonenumber** (`com.googlecode.libphonenumber:libphonenumber`) in shared
non-UI implementation code (for example data/shared validation behind a core interface), with Java 21,
packaging/module compatibility, license/security and metadata update checks. Parse a single full number
with an explicit region for national form or an explicit country calling code. Check possible length
**and valid numbering-plan structure**; parsing success or `isPossibleNumber` alone is insufficient.
Accept mobile, landline and normal business/toll-free numbers; do not restrict to mobile-only. This
establishes plausible usability, not assignment, reachability or ownership, and performs no call/SMS.
Use current library metadata and regression fixtures; metadata updates require regression review.

Region cannot be guessed from UI locale, user timezone, a three-digit prefix or tenant ID. Product must
choose a tenant/configured default-country policy or an explicit country selector. If no region exists,
ask for country code instead of rejecting a plausible number as invalid. International `+` inputs are
unambiguous; national dialing prefixes such as 00 must be interpreted with an explicit region.
Accept familiar spaces, parentheses, hyphens and dots, plus common extension suffixes (`ext`, `x`,
`;ext=`) via a constrained parser adapter. Reject prose, multiple numbers, arbitrary trailing content,
short/emergency/internal-only codes and incomplete numbers. Do not strip arbitrary letters first:
`garbage3035550123` must not become a valid phone. Vanity-number support requires an explicit later
product decision, not accidental library alpha conversion.

Parse extension separately and store in `Extension`; recommend ASCII digits 1–12 to match current safe
dialing action, pending product acceptance (schema permits 20). Preserve leading extension zeros. Reject
extension without main number. If inline and separate extensions disagree, ask user to choose rather
than concatenate or silently discard one. Same main number with different extensions is legitimate;
duplicate keys should include canonical main number **and extension**, plus approved kind semantics.

### Email recommendation

Trim surrounding whitespace before validation. Validate exactly one mailbox/address with a maintained
address validator/parser, no DNS/MX/SMTP/mailbox-existence check and no disposable-domain or TLD-length
allowlist. Prefer evaluating Apache Commons Validator `EmailValidator` (default public-domain syntax,
no local-only domains) with an explicit compatibility test corpus; do not just adopt a replacement regex.
Its supported quoted local parts, IDN/domain rules, maintained TLD metadata and international behavior
must be reviewed. Its default domain validator uses a TLD list; the final acceptance contract must
validate domain-label structure without making that list a rejection gate for legitimate newer TLDs.
This may require an adapter/custom domain policy or choosing a mailbox grammar parser instead. If they reject legitimate required forms, use a suitable mailbox parser and explicit
policy instead. No dependency is added in this task.

Support ordinary plus tags, apostrophes, subdomains, long valid TLDs and IDN domains. Do not require all
addresses to be lowercase or ASCII local parts merely for convenience. Product/transport review must
settle SMTPUTF8 (non-ASCII local parts), quoted local parts, address literals and local/internal domains.
Until those are resolved, classify unsupported-but-potentially-legitimate forms for review instead of
presenting a false universal “invalid” claim. Do not accept display-name/list forms in a single-address
field; a proper quoted local part is not a display name. Reject missing local/domain, multiple unquoted
`@`, unquoted whitespace, consecutive local dots, malformed domain labels and newline/header content.
Address length policy must reconcile RFC octet limits, IDN encoding and deployed column/API limits;
320 in a child schema is capacity, not proof that every 320-character mailbox is deliverable.

### Keep three responsibilities distinct

1. **Validation:** decide absent/usable/invalid/unsupported/unchanged legacy, with field-specific policy.
2. **Storage normalization:** only for approved new/changed valid values. Preserve trimmed display phone
   text and email casing; store E.164 main number in `NormalizedNumber` and separate extension. Domain
   case/IDNA handling belongs in a comparison representation; preserve local-part spelling. Existing
   lowercase whole-email keys and tenant user uniqueness are compatibility contracts: do not silently
   replace them or merge accounts. Decide case-comparison semantics separately. Do not retroactively
   normalize old values during unrelated edits or send NULL normalization as a repair.
3. **Display/launch:** show preserved display text, optionally a parser-rendered friendly preview.
   Formatting does not determine validity and should not rewrite stored text on blur. Build `tel:` from
   a validated canonical main number and extension, and `mailto:` from a safely encoded validated
   address. For legacy invalid values, show text/warning and omit unsafe Call/Email action. Do not hide
   the original value or treat an OS URI launch as proof of validity.

## Unknown or unavailable information: product decision required

The currently required human contact phone fields are intake Client Phone and conditional Caller Phone.
Client phone may legitimately be unavailable for a deceased/incapacitated client, a referral made by
someone else, a person without a phone, refusal to disclose, or a first inquiry where only email is
known. Even a separate caller may contact the firm without providing a return number. There is no
repository evidence of tenant policy authorizing these exceptions. User login email is currently
required for an active new account; a staff record without an account is a different product concept.

Recommended explicit design for review:

- Offer “Phone information unavailable” with a reviewed reason vocabulary (unknown/not provided/no
  phone, with sensitive narrative avoided) and a workflow status/follow-up obligation where needed.
  Store absence plus provenance/status separately; never insert `0`, `N/A`, a fake number or a shared
  employee number in the phone value. No blank structured child should be created.
- Product must decide whether each intake workflow may complete under that state, must remain a draft,
  or requires an authorized exception, and whether alternate email/caller contact is sufficient. Keep
  current requirements unchanged until that policy and persistence/API contract are approved.
- Reuse approved form-configuration/confirmation mechanisms if they can represent the state safely;
  otherwise scope an additive domain/schema design. Do not invent a tenant toggle, bypass token or
  authorization role. Permission alone does not establish a phone value as valid.
- For missing user login email, defer account activation or separate personnel/contact records from
  login identities if approved. Do not fabricate credentials or silently make active-login email optional.

## Existing invalid data and unrelated edits

Review is separate from ordinary saves. Validation rollout must not convert an unrelated edit into a
mandatory cleanup operation or silently rewrite data.

| Operation | Proposed treatment |
| --- | --- |
| New entity/point; copied/duplicated point; new value in intake merge/import | Validate all entered nonblank values and applicable requirements. Copying a bad old value to a **new row** is new input, not grandfathered history. |
| Existing point with unchanged phone/email/extension | Load authoritative baseline in transaction; compare value-bearing fields before normalization. Permit unrelated name, Notes, classifications, roles, order or other metadata changes with a nonblocking “Existing value needs review” warning. Preserve display and normalized value byte-for-character; do not rewrite child solely because aggregate includes it. |
| Existing value edited (including extension or intentional whitespace/case edit) | Validate the changed value. On success normalize only that value and persist atomically. An invalid replacement blocks its save and preserves staged text. Do not let a normalization-equivalence comparison hide a real edit. |
| Remove invalid point | Allow removal without first fixing its syntax; retain original history and lifecycle audit. Never validate a removed untouched history value as if it were a new contact point. |
| Restore a historical point or parent | Recommended: preserve retained unchanged values with warnings; syntax alone must not block unrelated lifecycle restoration. Continue blocking authorization/concurrency/ownership/structural conflicts. If restoring/changing a value together, validate the changed value. Do not make an invalid restored point a newly selected communication target without explicit correction; product must confirm restore/primary-selection behavior. |
| Make invalid legacy value primary/use for communication | Separate deliberate action: explain that correction is required for a usable communication target. Product must approve whether primary selection requires correction; grandfathering unrelated metadata must not promise dialability. |
| User authentication with existing identifier | Preserve lookup/auth compatibility; stricter new/changed-email rules must not block login to an existing account. Repair identity through approved admin/recovery flow, with duplicate checks and session implications reviewed. |

The server must determine “unchanged” from a tenant-owned authoritative row and concurrency token,
not from submitted row ID alone. Expand Contact point inventory beyond current deleted/RowVer pair
so it can compare original values and normalize only intentional changes. Keep security, exact-set,
RowVer, length where necessary for actual writes, and structural invariants enforced independently.
For omitted collections/fields preserve established ownership semantics; do not infer clear from absence.
Contact PATCH currently differs from Organization PATCH: product/API compatibility review must define
retain/clear/present semantics or introduce a versioned contract before relying on omission to protect
legacy values. Preserve basic-profile rows rather than remove-and-reinsert an unchanged value.

Cleanup should provide a tenant-scoped review queue with reason categories and stable parent/child IDs,
active/history state, provenance and reviewer disposition. Verify against the human source; allow
confirmed correction, removal, explicit unavailable state, or “needs follow-up.” Review unknown-country
and unsupported-email cases before classifying invalid. No automatic repair, bulk digit stripping,
mailbox probing or inferred tenant requirements. Use audited ordinary mutations with concurrency for
approved repairs; preserve history and review decisions. Rollout metrics should count categories,
not log phone/email values.

## Example acceptance and feedback contract

These are **proposed parser/UX fixture expectations**, not executed runtime results. National examples
assume an explicitly selected US region; international examples provide country codes. Mailbox
existence is never established. Optional blank is accepted as absence; required blank is rejected
unless an explicitly approved unavailable state applies.

| Input | Proposed outcome | User-facing message on failure |
| --- | --- | --- |
| ` (303) 555-0123 `; `303.555.0123`; `303-555-0123` (US region) | Accept familiar punctuation, trim display; canonical main `+13035550123`. | — |
| `+44 20 7946 0958`; `+61 2 9374 4000` | Accept international landline numbers according to parser metadata. | — |
| `+1 (303) 555-0123 ext. 0042`; separate extension `0042` | Accept; canonical main excludes extension, preserve extension leading zeros. | — |
| `0`; `0.`; `0000000000`; `123`; `+44`; `---`; `garbage3035550123` | Reject incomplete/unusable input or prose. | “Enter a complete phone number, including area or country code.” |
| `303-555-0123 / 720-555-0123` | Reject multiple values in one field. | “Enter one phone number per entry. Add another entry for additional numbers.” |
| National `020 7946 0958` with no configured/selected region | Needs country selection, not digit guessing. | “Choose a country or include the country calling code, such as +44.” |
| Extension alone; extension `abc`; inline `x42` and separate `99` | Reject missing number, bad extension or conflict. | “Enter a phone number before adding an extension.” / “Use digits for the extension (up to 12).” / “The two extensions differ. Keep one extension.” |
| Optional empty/whitespace phone/email; required empty Client Phone | Optional absence accepted; required value rejected. | “Client Phone Number is required. Enter a usable number.” |
| Required separate caller empty | Reject only when caller is not client. | “Caller Phone Number is required when Caller is Client is unchecked.” |
| ` Person+intake@Example.com `; `o'connor@example.org`; `person@sub.example.technology` | Accept reasonable structure, trim outer whitespace, preserve local spelling/case. | — |
| `person@bücher.de` | Accept IDN domain with approved IDNA handling. | — |
| `person`; `@example.com`; `person@`; `a@@example.com`; `a..b@example.com`; `a@-example.com` | Reject malformed structure. | “Enter an email address, such as name@example.com.” |
| `person @example.com`; `a@example.com, b@example.com`; `Name <a@example.com>`; CR/LF in value | Reject whitespace/list/display-name/header content for single address field. | “Enter one email address without a display name or extra text.” |
| `"a b"@example.com`; non-ASCII local part; address literal; `person@localhost` | Parser capability/product-policy review; do not silently equate unsupported with fabricated data. | If unsupported: “This address format needs review. Keep the original address and contact your administrator.” |
| Unchanged stored `0` or invalid old email during Notes edit | Permit save and retain value/history. | Nonblocking: “This existing phone/email needs review. Your other changes can still be saved.” |
| Changed invalid legacy value | Block only attempted invalid replacement. | “Enter a valid replacement, or cancel this field change to keep the existing value for review.” |
| Preferred Organization phone display exceeds mirror 60 characters | Reject with no truncation, pending capacity decision. | “This preferred phone number is too long to save (maximum 60 characters).” |
| Duplicate user login email | Preserve current active/inactive distinction and tenant scope. | “A user with this email already exists.” / “A user with this email already exists but is inactive. Reactivate the existing account instead.” |

Show field-local errors on blur/Save, not on each partial keystroke; leave editing/pasting free.
Use `ControlStyles.setInvalid`/accessible labels and an aggregate summary that focuses the first error.
Point dialogs should validate before adding a staged row; outer Save and persistence revalidate changes.
Return stable field/row error codes through the established safe HTTP error shape (an additive `fieldErrors`
extension needs API review); api.ts currently discards useful 400 details, so update its safe mapping.
Do not include submitted values, SQL or exceptions in messages, logs or live-update payloads.

## Suggested read-only audit (not executed)

No production data access, database connection, SQL migration or cleanup was performed. Design the audit
as a separately reviewed read-only report, first against an approved nonproduction copy with restricted
read-only permissions. An operator must establish tenant session/visibility using the existing runtime
and RLS rules; this plan does not authorize all-tenant administrative access or disable RLS.

1. Catalog preflight: inspect `sys.columns`, computed columns, checks (trust/enabled), unique indexes,
   FKs, triggers and relevant module dependencies for the four point tables, Contacts, Organizations and
   Users. Confirm Users phone candidate and `email_norm` computation, Unicode character versus byte
   widths, collations and deployed requiredness. Record catalog facts without credentials or row values.
2. Shape report: explicit `ShaleClientId` filters, separate active/removed parent and child counts, blank
   values, all-zero/very-short digit candidates, unsafe control characters, extension anomalies, invalid
   normalized shape and normalization absence. Missing normalized value alone is a review category,
   **not evidence that display is invalid**. SQL heuristics are screening, not phone/email validators.
3. Approved restricted read stream to the proposed Java parser, using explicit country policy. Classify
   valid, invalid, unsupported, missing region, missing required workflow evidence and duplicate candidate.
   Emit aggregates by tenant/table/kind/lifecycle/reason/parser version. Do not dump raw values to stdout,
   git, this document, logs or general artifacts. A secure human review UI may resolve selected IDs under
   existing sensitive-read auditing; parser output need not contain PHI.
4. Check authoritative-to-mirror equality for Organizations with the existing guarded read-only
   [Phase 3F.2 report](sql/verification/2026-09-10_organizations_phase3f2_compatibility_verification.sql),
   and retained restore invariants using the separate
   [Phase 3F.3 report](sql/verification/2026-09-11_organizations_phase3f3_lifecycle_verification.sql).
   These are drift/structure reports, not phone/email validity proofs; do not invoke their repair or
   backfill counterparts. Do not compare retired Contact scalars to current structured values as a gate.
5. Count potential canonical main+extension collisions without deduplicating; compare active versus
   history separately. User email uniqueness remains a distinct account-identity concern. Establish
   intake requiredness from workflow evidence: ordinary Contact absence alone is not a violation.
6. Review producer versions/adoption and external imports/utilities; quantify expected warning/error
   volume, then propose cleanup work for approval. Use bounded pages/stable IDs, approved resource budget
   and timeout; do not use `NOLOCK` for integrity conclusions. Catalog/table-count findings are limited
   to authorized visibility and snapshot timing.

Illustrative shape-count query only (run **only after** the future approved catalog/tenant preflight):

```sql
-- @TenantId is a bound, authorized tenant parameter; connection tenant context must match.
-- This screens value shapes, not mailbox/number validity. No row values are returned.
WITH PointShapes AS (
    SELECT N'ContactPhoneNumbers' AS SourceTable, Kind, IsDeleted,
           LEN(LTRIM(RTRIM(DisplayNumber))) AS ValueLength
    FROM dbo.ContactPhoneNumbers WHERE ShaleClientId = @TenantId
    UNION ALL
    SELECT N'ContactEmailAddresses', Kind, IsDeleted, LEN(LTRIM(RTRIM(EmailAddress)))
    FROM dbo.ContactEmailAddresses WHERE ShaleClientId = @TenantId
    UNION ALL
    SELECT N'OrganizationPhoneNumbers', Kind, IsDeleted, LEN(LTRIM(RTRIM(DisplayNumber)))
    FROM dbo.OrganizationPhoneNumbers WHERE ShaleClientId = @TenantId
    UNION ALL
    SELECT N'OrganizationEmailAddresses', Kind, IsDeleted, LEN(LTRIM(RTRIM(EmailAddress)))
    FROM dbo.OrganizationEmailAddresses WHERE ShaleClientId = @TenantId
)
SELECT SourceTable, Kind, IsDeleted, COUNT_BIG(*) AS InspectedCount,
       SUM(CASE WHEN ValueLength = 0 THEN CONVERT(bigint, 1) ELSE 0 END) AS BlankFindingCount,
       SUM(CASE WHEN ValueLength BETWEEN 1 AND 3 THEN CONVERT(bigint, 1) ELSE 0 END) AS ShortReviewCount
FROM PointShapes GROUP BY SourceTable, Kind, IsDeleted;
```

A later report should join tenant-qualified parent IDs to distinguish removed parents and separately
count Users and Organization mirrors. Do not treat `ShortReviewCount` as rejection criteria. Neither
this SQL nor the existing verification reports was run in this task.

## Audit compatibility and security review

This document creates no runtime mutation or sensitive-data read event. Future validation rejection
must produce no success mutation audit. Existing Contact aggregate, intake and Organization aggregate
workers append entity-action audits on the business connection before commit; retain parent/child,
tenant, actor and case context, and rollback when audit fails. Existing PHI auditing of personal fields
must remain intact. User admin update uses `USER/UPDATED`; its ID/role metadata must not gain email/phone.
Contact basic replacement and User basic-profile save do **not** provide the same child/user
transaction-bound entity-action audit boundary as complete aggregates. Consolidating these bypasses
must explicitly close that gap, not add authoritative UI or asynchronous audit inserts.

Use existing entity/action vocabulary where compatible (`CONTACT_PHONE_NUMBER`,
`CONTACT_EMAIL_ADDRESS`, `ORGANIZATION_PHONE`, `ORGANIZATION_EMAIL`, parent `USER` and existing
create/update/remove/restore actions). Inspect `EntityActionAuditEvent`/`EntityActionAuditDao` and deployed
allowlists before use. Sensitive review views must use established PHI/PHI-read/administrative-read
boundaries appropriate to the domain. Raw contact values, commands/DTOs, canonical values, RowVers,
SQL and exception text are forbidden in entity-action metadata. If unknown-status or review-disposition
mutations cannot be represented by existing allowlists, document exact vocabulary/schema gap and defer a
scoped enhancement; do not invent timeline events or ad-hoc logs. Counts/reason codes may be proposed
only after allowlist review. Preserve explicit tenant filters and initialized connection context,
optimistic concurrency, soft-delete history and existing post-commit refresh/live publication.

## Decisions before implementation

| Decision owner | Required decision / recommended starting point |
| --- | --- |
| Product / tenant-policy owners | Can intake proceed with explicit unavailable phone, with which reasons/alternate contact/follow-up, or must remain draft? Client and separate caller decisions must be explicit. No silent optionality change. |
| Product / platform | National-number region selection/default source; supported countries and short/vanity-number scope. Prefer international parser and explicit region, no locale inference. |
| Product / communication owners | Restore and newly selecting invalid legacy point as primary/communication target; recommended restore preservation with warning, require correction for deliberate communication use. |
| Product / platform | Extension limit/suffix acceptance and canonical duplicate semantics across kinds. Recommend 1–12 digits, separate field, main+extension comparison. |
| Product / platform | Supported quoted/local/IDN/SMTPUTF8/literal email forms and byte-length contract. Do not reject legitimate required formats through a convenience regex. |
| API / data owners | Contact PATCH retain/clear semantics, safe basic-row ownership, RowVer exposure/versioning and field-errors contract; avoid destructive replacement of unchanged points. |
| Data / platform | Parser dependency location given dependency-free core, mirror width strategy, email comparison/normalization compatibility and collation, externally installed writers. No migration until catalog and compatibility are reviewed. |
| Product / operations | Review/cleanup ownership, secure reviewer access, unavailable provenance and retention, adoption/rollout sequence, and recovery for legacy login identity. |

## Staged implementation and testing plan

Each stage is a future task, not work performed by this PR.

1. **Contract and decision stage:** approve the decisions above; inventory external producers and deployed
   schema read-only; set parser/region/email corpus, stable field paths and existing-data transition.
   Map all affected tests before editing code. No new broad syntax database CHECK or data backfill.
2. **Shared validator stage:** add pinned parser adapter behind core contracts; tests for required/optional
   blanks, punctuation, national/international context, valid/possible distinction, `0`/all-zero/short/prose,
   extension/conflict, reasonable email syntax and unsupported forms, Unicode/control characters,
   lengths and no network calls. Test validation separately from normalization and formatting.
3. **Authoritative persistence stage:** apply the same contract to complete Contact/Organization saves,
   Contact basic create/update, Organization legacy adapters, direct User create/admin/basic update,
   and both intake create/merge **before mutation**. Implement baseline comparison and no rewrite for
   unchanged legacy rows, including deleted rows; allow removal without syntax repair. Preserve
   RowVer/tenant/actor/primary/exact-set invariants. Fix incoming merge primary/order handling and
   optional SQL binding shape alongside focused regressions. Close basic-save audit transaction gaps.
4. **UI/API stage:** shared desktop field feedback and staged-row checks, conditional intake requirement,
   Contact/Organization rows and extension inputs, User pencil and whole-row paths, browser preview and
   safe server field errors. Keep login legacy-compatible. Match storage/mirror lengths and ensure errors
   keep drafts, cancel writes nothing, and success refresh/live invalidation happens once after commit.
   Unavailable-information UI/persistence is a separately approved contract, not a magic string.
5. **Read-only audit/cleanup stage:** run reviewed report on nonproduction, triage secure findings, measure
   unchanged warnings, and offer audited corrections. Never make report execution an implicit production
   cleanup permission. Verify all writers before controlled rollout; old direct-JDBC clients need an
   explicit adoption/enforcement plan because HTTP validation cannot protect their writes.
6. **Rollout stage:** verify focused behavioral/persistence/UI/API tests and critical local suite, inspect
   safe audit metadata and rollback behavior, then staged deployment and warning/error counts. Rollback
   removes enforcement behavior without reverting data into retired Contact scalars. A later schema
   migration requires its own catalog proof, data-impact review and authorization.

### Existing test-impact inventory

Inspected related tests statically. These tests describe the old contract; they were **not executed or
changed** here. Current fixture numbers such as `555-0100` or `+44 20 1234` may not be valid full numbers
under a real parser; future implementation must review them rather than weakening the parser to keep
source/fixture expectations green.

| Area | Existing tests to review/extend; missing behavioral proof |
| --- | --- |
| Intake | `shale-data/.../dao/NewIntakeContactPersistenceRegressionTest.java` exercises independent client/caller bindings, blank optional points, same-connection audit and rollback with recording JDBC; `NewIntakeDuplicateMergeContractTest.java` checks normalized duplicate lookup/additive merge/transactions. Add direct create+merge required/optional failures, caller-is-client rules, copied value validation, valid new points alongside invalid old points, primary/order collisions and correct two-column bindings when normalization is NULL. `CaseDao.insertIntakeContactPoint` currently chooses placeholder count by **secondValue != null** even when `valueColumns` contains two columns; test future NULL-normalization behavior explicitly. UI `NewIntakeControllerConnectivityPreflightTest.java` and date/duplicate tests are not phone usability coverage. |
| Contact core/data | `shale-core/.../service/ContactCompleteProfileCommandTest.java`, `ContactPointModelsContractTest.java` protect values/tokens, not usability. Data `ContactAggregateMutationContractTest.java`, `ContactCompleteAggregateContractTest.java`, `ContactMutationContractTest.java`, `ContactPhase2CAContactPointsMigrationContractTest.java`, `ContactPhase3BNoLegacyRuntimeContractTest.java` protect exact sets, tenancy, transaction/audit and retirement. Extend behavioral tests for direct basic/aggregate parity, changed versus unchanged invalid active/history, removal/restore, extension duplicates, no unintended normalized rewrite and SQL rollback. |
| Organization | Data `OrganizationAggregateRowVersionTest.java` includes `legacyBackfilledPhoneDoesNotBreakNameNotesOrPhoneEdits` and checks unchanged backfilled rows are not rewritten. `OrganizationsPhase3F2AuthorityContractTest.java`, `OrganizationStructuredContactMethodsMigrationContractTest.java`, `OrganizationStructuredContactReadContractTest.java`, `OrganizationServiceAdapterTest.java` cover authority/schema/delegation. Preserve these contracts; add direct structured/legacy validator parity, preferred mirror length failure, child/parent restore warnings, no audit or child rewrite for unchanged values, and atomic rollback on invalid changed input/audit failure. |
| User / auth | `UserDaoCreateUserTest.java` checks required names/email, normalization, hashing, duplicates and computed-field omission, not full email syntax. `UserManagementRemovalContractTest.java`, `AuthUserLifecycleSecurityTest.java`, server `AuthControllerTest.java`, `DesktopSessionControllerTest.java` must retain auth/lifecycle safety. Add basic/admin parity, optional phone, duplicate normalized identity, unchanged bad-email role edit, legitimate long/IDN addresses, protected login compatibility and recovery/session behavior. |
| Desktop | `ContactStructuredPresentationTest.java`, `ContactPhase2BViewContractTest.java`, `OrganizationStructuredPresentationTest.java`, `OrganizationEditEntryPointTest.java`, `NewOrganizationSemanticControlsTest.java`, `UserManagementTablePresentationTest.java`, `UserWindowsPhase8IPresentationContractTest.java`, `ContactExternalActionsTest.java`, `ContactMethodDisplayCardTest.java` protect source/UI/action wiring. Add focused editor feedback, paste/blur/Save, required indicators, draft retention and unchanged-history warning checks; visual geometry remains advisory. |
| HTTP/web | `ApiReadControllerTest.java` currently accepts basic Contact `555-0100` and verifies trim/command routing. Test POST/PATCH direct requests and omission/clear semantics, every field path, safe 400 mapping, shared service rejections, atomic persistence and legacy unchanged values. Browser forms use native email constraints plus `includes('@')`; add shared fixture-driven client cases and server-error rendering using established web tooling. Do not claim browser/server parity from HTML input types. |
| Schema/audit | Existing migration/audit allowlist contracts for Contact Phase 2B/2C-B and Organization Phase 3C plus `EntityActionAuditEventTest.java`/`EntityActionAuditEvent.java` must preserve PHI-free metadata and tenant/actor/context. Future DB verification uses a disposable nonproduction DB; mocks/source tests cannot prove live CHECK, index, trigger, collation or RLS behavior. |

For implementation: run modified focused tests first using the existing cross-module Maven form
`mvn -pl <module> -am -Dtest=<affected classes> -Dsurefire.failIfNoSpecifiedTests=false test`;
then `python build/test-selection/select_tests.py --base <base> --head HEAD --format markdown`, the
selected affected-area/dependency commands, and `mvn test`. Full suite only when relevant/informational
or requested; UI rendered geometry only in advisory visual profile. Do not add GitHub test workflows.

### Validation of this documentation PR

Only documentation consistency, source-reference checks, selector inspection and `git diff --check`
are appropriate to this inspection-only change. No production/test code changed; no Maven/runtime,
browser/JavaFX or database verification is claimed. The executed check results are recorded in the PR
body. Suggested audit and implementation commands above are future work, not executed validation.
