# Contacts Phase 3B legacy-column inventory

The unchanged Phase 3A production audit result for tenant 7, `PASS_READY_FOR_PHASE_3B`, is the
immutable pre-cutover baseline proving that structured data was complete and consistent when
compatibility writes stopped. It must not be rerun as a post-Phase-3B parity gate: valid structured-only
mutations can make legacy scalar values stale by design. Phase 3B did not access any database and does
not change or add SQL migrations.
`Contacts.DisplayName` remains the stored base current name. `ContactAddresses.LegacyAddressText`
remains part of the authoritative structured-address record.

The repository was searched for exact, word-bounded occurrences of `PhoneCell`, `PhoneHome`,
`PhoneWork`, `EmailPersonal`, `EmailWork`, `EmailOther`, `AddressHome`, `AddressWork`, `AddressOther`,
and `IsExpert` in production/test Java, SQL, documentation, fixtures, FXML, TypeScript, and schema
contracts. Every resulting group has the following disposition.

| Class | Exact locations and Phase 3B disposition |
|---|---|
| 1 — production compatibility writes | `ContactMutationDao.projectLegacy`, its aggregate-save invocation, `setExpert`, `recomputeExpert`, and their assignment/aggregate hooks were deleted. No conditional, metadata-driven, retry, or feature-flag variant remains. |
| 2 — production reads | Current-Contact scalar reads in `CaseDao` party cards, selectors, requested-from choices, case-link sharing, and search were replaced with tenant-correlated active structured-table projections ordered by primary, sort order, and ID. Phase 2E `ContactDao` structured reads remain authoritative. |
| 3 — request/DTO/schema boundary | `ContactDao` no longer discovers or assigns Contact email, phone, or address columns. The structured Contact detail address projection uses a presentation alias rather than a retired column name. Service, adapter, and server address presentation fields use the same neutral name. |
| 4 — historical SQL/documentation retained | Phase 1A foundation/backfill and verification SQL, Phase 2C-A backfill and verification SQL, the unchanged Phase 3A readiness audit, the Phase 3A runbook, `database-schema.md`, `contact-management.md`, and the Phase 2E inventory intentionally retain exact historical references. They are not runtime Java and were not rewritten. |
| 5 — snapshots/unrelated aggregates retained | `CaseDao` retains only the three exact `CaseContacts` snapshot insert column names (`AddressHome`, `PhoneCell`, `EmailPersonal`). `UserDao` retains its `PhoneCell` candidate for the unrelated Users schema. User and Organization contact fields are unchanged. Generated document calls named `addressHome` are historical case/client presentation models, not `dbo.Contacts` columns. |
| 6 — tests/fixtures | Phase 1A, Phase 2C-A, Phase 2E, and Phase 3A historical contract tests retain their quoted source expectations. The mutation contract now rejects the Expert scalar bridge. The Phase 3B boundary contract allows only the exact snapshot/User cases above and rejects every other production-Java occurrence. Server and structured-presentation fixtures were renamed only where they model the current structured Contact address presentation. |
| 7 — false positives | `EmailAddress`, `LegacyAddressText`, structured `address`, generic Organization/User fields, and documentation prose are not retiring `dbo.Contacts` columns. They remain valid. |

## Deployment boundary

1. Merge and release the tested Phase 3B application.
2. Deploy it to every Contact-writing desktop, server, scheduled task, importer, integration, and
   administrative utility.
3. Block or retire unsupported older writers.
4. Observe the approved stability/adoption window.
5. Run a new, separately reviewed Phase 3C pre-drop audit.
6. Only after that audit passes may the physical retirement migration be prepared and reviewed.

The Phase 3C pre-drop audit has a different purpose from Phase 3A. It must fail closed unless:

- every Contact-writing producer has adopted the no-legacy-write application;
- no runtime application SQL reads or writes any of the ten retiring columns;
- no database module, trigger, view, function, procedure, computed column, constraint, index, foreign
  key, or security predicate depends on them;
- structured Contact tables satisfy tenant, ownership, lifecycle, primary, normalization, ordering,
  and classification integrity rules;
- required schema and RLS protection remain valid;
- all ten retiring columns are still present before the drop, with partial retirement rejected;
- backups, a rollback procedure, adoption evidence, and operator approval are present; and
- results remain tenant-scoped and PHI-safe.

The Phase 3C audit must not require equality between authoritative structured values and intentionally
stale legacy scalar values. The existing Phase 3A audit remains unchanged as immutable pre-cutover
evidence; Phase 3C must introduce a new, separately reviewed audit rather than weakening or rewriting it.

Rolling back to a pre-Phase-3B application after any structured-only mutation has occurred is unsafe
without a separately reviewed structured-to-legacy reconciliation procedure. Phase 3B does not create
or authorize that reconciliation SQL.

Do not rerun the Phase 2C-A backfill after Phase 3B is authoritative unless a separately reviewed
recovery procedure explicitly requires it. Phase 3C remains the separately reviewed physical-column
retirement; this implementation neither authorizes nor performs it.
