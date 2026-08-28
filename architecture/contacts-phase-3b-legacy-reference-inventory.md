# Contacts Phase 3B legacy-column inventory

The unchanged Phase 3A production audit result for tenant 7, `PASS_READY_FOR_PHASE_3B`, is the
pre-Phase-3B baseline. Phase 3B did not access any database and does not change or add SQL migrations.
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

1. Merge and release Phase 3B.
2. Deploy it to every desktop, server, scheduled process, importer, and integration that can mutate Contacts.
3. Confirm every producer has adopted it.
4. Prevent unsupported older clients from mutating Contacts.
5. Observe the separately agreed adoption/stability window.
6. Rerun the unchanged Phase 3A readiness and dependency audit.
7. Confirm backups, rollback plan, deployment evidence, and operator approval.
8. Only then begin a separately reviewed final retirement migration.

Do not rerun the Phase 2C-A backfill after Phase 3B is authoritative unless a separately reviewed
recovery procedure explicitly requires it. Phase 3C remains the separately reviewed physical-column
retirement; this implementation neither authorizes nor performs it.
