# Contacts Phase 2E legacy-reference inventory

This inventory is the production boundary for the structured-data cutover. It was produced by searching every
Java, SQL, FXML, test, fixture and documentation file in `shale-core`, `shale-data`, `shale-ui`, `shale-desktop`,
`shale-server`, and `shale-updater`. `DisplayName` is intentionally excluded from retirement: it remains the stored
base current name and credentials are composed at presentation time.

## Classification

| Classification | Occurrences and disposition |
|---|---|
| 1. Live production reads cut over | `ContactDao` directory listing, global Contact search, directory lookup, paged cards, and Contact detail/API/document projections now select active rows from `ContactPhoneNumbers`, `ContactEmailAddresses`, and `ContactAddresses`. Primary, then `SortOrder`, then ID determines the current value. There is no scalar fallback. Global search uses normalized structured phone/email values. |
| 2. Compatibility dual-write retained | `ContactMutationDao.projectLegacy` writes `PhoneCell`, `PhoneHome`, `PhoneWork`, `EmailPersonal`, `EmailWork`, `EmailOther`, `AddressHome`, `AddressWork`, and `AddressOther`. `setExpert`/`recomputeExpert` write `Contacts.IsExpert`; Expert is derived by `ContactTypes.SystemKey = 'expert'`. Basic/create compatibility request fields and schema-column discovery remain write-only boundaries. |
| 3. Migration/backfill/retirement retained | `docs/sql/2026-08-24_contacts_foundation_phase1a.sql`, its verification script, Phase 2C-A contact-point migration, schema documentation, and migration contract tests intentionally reference legacy columns. They were not changed. |
| 4. Historical snapshot/audit retained | Case captions, task and audit actor labels, generated document data already persisted, notification snapshots, and audit descriptions remain immutable. Organization and User phone/email/address columns describe those aggregates, not Contacts, and remain authoritative for them. |
| 5. Obsolete code/tests removed | The three live directory projections and Contact-detail projection no longer use schema-discovered scalar Contact email, phone, or home address columns. Their old scalar search dependency was removed as well. |
| 6. False positives | Structured model fields (`EmailAddress`, `AddressLine1`, etc.) and UI calls such as `row.email()`/`row.phone()` consume already-structured projections. `State`, `City`, and `DisplayName` also occur in unrelated organizations, users, runtime state, case-link display names, and historical labels. `isExpert` text outside the Contact compatibility boundary is absent from production Java. No `SELECT c.*` Contact query was found. |

## Production path map and loading evidence

`ContactServiceAdapter` supplies the server API and UI search/selectors from these DAO projections. Directory cards
use one bounded page query containing correlated `TOP(1)` projections and one bounded credential enrichment query.
Global Contact search and single-contact lookup each use one projection query. Contact detail performs a bounded
single-contact aggregate load; it never loads all Contacts and never performs one contact-point query per card.

Effective names continue to use `ContactNamePresentation`: stored base `DisplayName` plus active credentials in
authoritative display order. Case-party Contact Cards use the same enriched summary path. Historical labels are not
passed back through current-name composition.

## Deliberate Phase 3 remainder

After separate approval and deployed verification, Phase 3 may stop scalar compatibility writes, remove legacy
request/DTO overloads and schema discovery, add the retirement migration that drops the nine scalar contact-point
columns and `Contacts.IsExpert`, and delete the Phase 1 backfill/verification allowances. Phase 2E adds no migration
and performs no database access.
